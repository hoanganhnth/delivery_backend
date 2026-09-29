package com.delivery.web_bff.application;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.auth.LoginApplicationService;
import com.delivery.web_bff.domain.session.RefreshInProgressException;
import com.delivery.web_bff.domain.session.Security;
import com.delivery.web_bff.domain.session.SessionFactory;
import com.delivery.web_bff.domain.session.SessionMaterial;
import com.delivery.web_bff.domain.session.SessionRejectedException;
import com.delivery.web_bff.domain.session.WebSession;
import com.delivery.web_bff.proxy.ApiProxyPolicyApplicationService;
import com.delivery.web_bff.proxy.ProxyForwardingApplicationService;
import com.delivery.web_bff.session.AccessTokenApplicationService;
import com.delivery.web_bff.session.CurrentSessionApplicationService;
import com.delivery.web_bff.session.LogoutApplicationService;
import com.delivery.web_bff.session.RefreshApplicationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebBffApplicationServicesTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void loginCreatesAndPersistsOpaqueSessionAndRejectsRoleMismatch() {
        Fakes f = new Fakes();
        LoginApplicationService login = new LoginApplicationService(f, f.factory(), f, f);
        SessionMaterial material = login.execute(new Ports.LoginCommand("a@b.test", "pw", null, "web", "device"));
        assertEquals(42, material.session().principalId());
        assertSame(material.session(), f.saved);
        assertThrows(SessionRejectedException.class, () -> login.execute(null));
        f.tokens = new Ports.AuthenticatedTokens("a", "r", 42, "a@b.test", "CUSTOMER");
        assertThrows(SessionRejectedException.class, () -> login.execute(new Ports.LoginCommand("a", "p", "ADMIN", null, null)));
        f.tokens = new Ports.AuthenticatedTokens("a", "r", 42, "a@b.test", "ADMIN");
        assertNotNull(login.execute(new Ports.LoginCommand("a", "p", " ", null, null)));
    }

    @Test
    void policyClosesInvalidPathsAndAllowsOnlyKnownResources() {
        var policy = new ApiProxyPolicyApplicationService();
        assertTrue(policy.execute(Ports.HttpVerb.GET, "/api/users/1"));
        assertTrue(policy.execute(Ports.HttpVerb.GET, "/api/auth/sessions"));
        assertTrue(policy.execute(Ports.HttpVerb.GET, "/api/auth/sessions/abc"));
        assertTrue(policy.execute(Ports.HttpVerb.POST, "/api/auth/firebase/chat-token"));
        assertTrue(policy.execute(Ports.HttpVerb.POST, "/api/auth/admin/accounts/7/block"));
        assertTrue(policy.execute(Ports.HttpVerb.GET, "/api/auth/accounts/7"));
        assertFalse(policy.execute(null, "/api/users"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, null));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/v1/users"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/users/../secrets"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/users//1"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/users/%2e%2e"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/users;drop"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/internal/health"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/auth/login"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/auth/registrations/1"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/auth/nope"));
        assertFalse(policy.execute(Ports.HttpVerb.GET, "/api/unknown"));
    }

    @Test
    void accessAndCurrentSessionApplySessionAndCsrfRules() {
        Fakes f = new Fakes();
        var access = new AccessTokenApplicationService(f, f, f);
        assertThrows(SessionRejectedException.class, () -> access.execute(" ", null, false));
        assertEquals("access", access.execute("sid", null, false));
        assertEquals("access", access.execute("sid", "csrf", true));
        assertThrows(SessionRejectedException.class, () -> access.execute("sid", "bad", true));
        f.active = Optional.empty();
        assertThrows(SessionRejectedException.class, () -> access.execute("sid", null, false));

        f.active = Optional.of(f.session());
        var current = new CurrentSessionApplicationService(f, f);
        assertTrue(current.execute(null).isEmpty());
        assertTrue(current.execute(" ").isEmpty());
        assertEquals(Optional.of(new UseCases.SessionView(42, "a@b.test", "ADMIN", 1)), current.execute("sid"));
        f.active = Optional.empty();
        assertTrue(current.execute("sid").isEmpty());
    }

    @Test
    void logoutRevokesLocallyAndSwallowsUpstreamFailure() {
        Fakes f = new Fakes();
        var logout = new LogoutApplicationService(f, f, f, f);
        assertThrows(SessionRejectedException.class, () -> logout.execute(null, "csrf"));
        assertThrows(SessionRejectedException.class, () -> logout.execute("sid", "bad"));
        logout.execute("sid", "csrf");
        assertNotNull(f.saved.revokedAt());
        assertEquals("refresh", f.loggedOutRefresh);
        f.saved = f.session();
        f.logoutFails = true;
        assertDoesNotThrow(() -> logout.execute("sid", "csrf"));
    }

    @Test
    void refreshClaimsRotatesAndHandlesConcurrentOrUnknownOutcomes() {
        Fakes f = new Fakes();
        var refresh = new RefreshApplicationService(f, f, f, f);
        UseCases.SessionView view = refresh.execute("sid", "csrf");
        assertEquals(2, view.generation());
        assertEquals(1, f.claims);
        assertThrows(SessionRejectedException.class, () -> refresh.execute(null, "csrf"));

        f.saved = f.session(); f.active = Optional.of(f.saved); f.claimResult = false;
        assertThrows(RefreshInProgressException.class, () -> refresh.execute("sid", "csrf"));
        f.claimResult = true; f.refreshFails = true; f.saved = f.session(); f.active = Optional.of(f.saved);
        assertThrows(SessionRejectedException.class, () -> refresh.execute("sid", "csrf"));
        assertNotNull(f.saved.revokedAt());

        f.refreshFails = false; f.saved = f.session(); f.active = Optional.of(f.saved); f.csrfOk = false;
        assertThrows(SessionRejectedException.class, () -> refresh.execute("sid", "bad"));
        f.csrfOk = true; f.saved = f.session(); f.active = Optional.of(f.saved); f.disappear = true;
        assertThrows(SessionRejectedException.class, () -> refresh.execute("sid", "csrf"));
        f.disappear = false; f.saved = f.session(); f.active = Optional.of(f.saved); f.mutateDuringRefresh = true;
        assertThrows(SessionRejectedException.class, () -> refresh.execute("sid", "csrf"));
    }

    @Test
    void proxyRejectsUnsafeRequestsAndForwardsSanitizedMutationAndRead() {
        Fakes f = new Fakes();
        var forwarding = new ProxyForwardingApplicationService(new ApiProxyPolicyApplicationService(), f, f);
        assertThrows(RuntimeException.class, () -> forwarding.execute(null));
        assertThrows(RuntimeException.class, () -> forwarding.execute(new UseCases.ProxyRequest(
                Ports.HttpVerb.GET, "/api/nope", null, null, null, "sid", null)));
        Map<String, List<String>> headers = Map.of("Accept", List.of("json"), "Authorization", List.of("drop"));
        var response = forwarding.execute(new UseCases.ProxyRequest(Ports.HttpVerb.GET, "/api/users", "x=1", headers,
                null, "sid", null));
        assertEquals(200, response.status());
        assertFalse(f.lastHeaders.containsKey("Authorization"));
        forwarding.execute(new UseCases.ProxyRequest(Ports.HttpVerb.POST, "/api/users", null, Map.of(), new byte[] {1}, "sid", "csrf"));
        assertEquals("csrf", f.lastCsrf);
    }

    private static WebSession session(String rawSessionId, String csrf) {
        return new WebSession(Security.hash(rawSessionId), "accessCipher", "refreshCipher", "v1", 42,
                "a@b.test", "ADMIN", Security.hash(csrf), 1, NOW.plus(Duration.ofHours(1)), NOW);
    }

    static final class Fakes implements Ports.Authentication, Ports.Sessions, Ports.TokenProtection,
            Ports.Clock, Ports.ProxyForwarding, UseCases.AccessTokenResolution {
        Ports.AuthenticatedTokens tokens = new Ports.AuthenticatedTokens("access", "refresh", 42, "a@b.test", "ADMIN");
        WebSession saved = WebBffApplicationServicesTest.session("sid", "csrf");
        Optional<WebSession> active = Optional.of(saved);
        boolean claimResult = true, refreshFails, logoutFails, csrfOk = true, disappear, mutateDuringRefresh;
        int claims;
        String loggedOutRefresh, lastCsrf;
        Map<String, List<String>> lastHeaders = Map.of();

        SessionFactory factory() { return new SessionFactory(this, size -> new byte[size], Duration.ofHours(1)); }
        WebSession session() { return WebBffApplicationServicesTest.session("sid", "csrf"); }
        public Ports.AuthenticatedTokens login(Ports.LoginCommand command) { return tokens; }
        public Ports.AuthenticatedTokens refresh(String refreshToken) {
            if (refreshFails) throw new IllegalStateException("unknown");
            return new Ports.AuthenticatedTokens("new-access", "new-refresh", 42, "a@b.test", "ADMIN");
        }
        public void logout(String refreshToken) { loggedOutRefresh = refreshToken; if (logoutFails) throw new IllegalStateException(); }
        public void save(WebSession session) { saved = session; }
        public Optional<WebSession> active(String hash, Instant now) { return active; }
        public Optional<WebSession> byHash(String hash) {
            if (disappear) return Optional.empty();
            if (mutateDuringRefresh) return Optional.of(new WebSession(saved.sessionHash(), saved.accessTokenCipher(), saved.refreshTokenCipher(),
                    saved.encryptionKeyVersion(), saved.principalId(), saved.email(), saved.role(), saved.csrfHash(),
                    saved.generation() + 1, saved.expiresAt(), saved.createdAt()));
            return Optional.of(saved);
        }
        public boolean claimRefresh(String hash, long generation, Instant now, Instant leaseUntil) { claims++; return claimResult; }
        public Instant now() { return NOW; }
        public String keyVersion() { return "v1"; }
        public String protect(String plaintext) { return plaintext + "-cipher"; }
        public String reveal(String protectedValue) { return protectedValue.replace("Cipher", "").replace("-cipher", ""); }
        public String execute(String rawSessionId, String csrfToken, boolean mutation) { lastCsrf = csrfToken; return "bearer"; }
        public Ports.ForwardedResponse forward(Ports.HttpVerb method, String path, String query,
                Map<String, List<String>> headers, byte[] body, String bearerToken) {
            lastHeaders = headers; return new Ports.ForwardedResponse(200, Map.of(), body);
        }
    }
}
