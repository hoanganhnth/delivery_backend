package com.delivery.web_bff;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.infrastructure.session.JpaWebSessionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = WebBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "management.server.port=0", "web-bff.gateway-base-url=http://127.0.0.1:1",
        "web-bff.allowed-origins=https://bff.test", "web-bff.session-max-age-seconds=600",
        "web-bff.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "web-bff.encryption-key-version=runtime-test", "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(WebBffRuntimeIntegrationTest.AuthFixture.class)
class WebBffRuntimeIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JpaWebSessionRepository sessions;
    @Autowired AuthStub auth;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired Ports.Sessions store;
    @Autowired Ports.TokenProtection tokens;
    @Autowired com.delivery.web_bff.domain.session.SessionFactory factory;
    final HttpClient http = HttpClient.newHttpClient();

    @Test void actualHttpSessionLifecyclePersistsEncryptedTokensAndRejectsInvalidBrowserCredentials() throws Exception {
        assertThat(send("POST", "/bff/session/login", "{}", null, null, null).statusCode()).isEqualTo(403);
        String credentials = "{\"email\":\"owner@example.test\",\"password\":\"fixture-password\",\"role\":\"SHOP_OWNER\"}";
        var login = send("POST", "/bff/session/login", credentials, null, null, "https://bff.test");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control")).contains("no-store");
        var body = json.readTree(login.body());
        assertThat(body.path("authenticated").asBoolean()).isTrue();
        assertThat(body.path("principalId").asLong()).isEqualTo(21);
        assertThat(login.body()).doesNotContain("fixture-access", "fixture-refresh");
        String csrf = body.path("csrfToken").asText();
        String cookie = login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("__Host-delivery-session="))
                .findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
        cookie = cookie.split(";", 2)[0];
        var entity = sessions.findById(com.delivery.web_bff.domain.session.Security.hash(cookie.substring(cookie.indexOf('=') + 1))).orElseThrow();
        assertThat(entity.getAccessTokenCipher()).doesNotContain("fixture-access");
        assertThat(entity.getRefreshTokenCipher()).doesNotContain("fixture-refresh");
        assertThat(entity.getGeneration()).isEqualTo(1);
        assertThat(send("GET", "/bff/session", null, cookie, null, null).body()).contains("\"authenticated\":true");
        assertThat(send("POST", "/bff/session/refresh", "{}", cookie, "wrong", "https://bff.test").statusCode()).isEqualTo(401);
        var refresh = send("POST", "/bff/session/refresh", "{}", cookie, csrf, "https://bff.test");
        assertThat(refresh.statusCode()).isEqualTo(200);
        assertThat(json.readTree(refresh.body()).path("sessionVersion").asLong()).isEqualTo(2);
        assertThat(auth.refreshes.get()).isEqualTo(1);
        var logout = send("POST", "/bff/session/logout", "{}", cookie, csrf, "https://bff.test");
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(auth.logouts.get()).isEqualTo(1);
        assertThat(sessions.findById(entity.getSessionHash()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(send("GET", "/bff/session", null, cookie, null, null).body()).contains("\"authenticated\":false");
    }

    @Test void logoutBetweenRefreshReadAndWriteCannotResurrectSession() {
        var now = java.time.Instant.now();
        var material = factory.create("old-access", "old-refresh", 21, "owner@example.test", "SHOP_OWNER", now);
        store.save(material.session());
        Ports.Authentication upstream = new Ports.Authentication() {
            public Ports.AuthenticatedTokens login(Ports.LoginCommand command) { throw new UnsupportedOperationException(); }
            public Ports.AuthenticatedTokens refresh(String refresh) {
                return new Ports.AuthenticatedTokens("new-access", "new-refresh", 21, "owner@example.test", "SHOP_OWNER");
            }
            public void logout(String refresh) { }
        };
        var logout = new com.delivery.web_bff.session.LogoutApplicationService(store, upstream, tokens, java.time.Instant::now);
        Ports.Sessions interleaving = new Ports.Sessions() {
            public void save(com.delivery.web_bff.domain.session.WebSession session) { store.save(session); }
            public java.util.Optional<com.delivery.web_bff.domain.session.WebSession> active(String hash, java.time.Instant time) {
                return store.active(hash, time);
            }
            public java.util.Optional<com.delivery.web_bff.domain.session.WebSession> byHash(String hash) {
                var snapshot = store.byHash(hash);
                logout.execute(material.rawSessionId(), material.rawCsrfToken());
                return snapshot;
            }
            public java.util.Optional<com.delivery.web_bff.domain.session.WebSession> mutate(String hash,
                    java.util.function.Consumer<com.delivery.web_bff.domain.session.WebSession> transition) {
                logout.execute(material.rawSessionId(), material.rawCsrfToken());
                return store.mutate(hash, transition);
            }
            public boolean claimRefresh(String hash, long generation, java.time.Instant time, java.time.Instant lease) {
                return store.claimRefresh(hash, generation, time, lease);
            }
        };
        var refresh = new com.delivery.web_bff.session.RefreshApplicationService(interleaving, upstream, tokens, java.time.Instant::now);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> refresh.execute(material.rawSessionId(), material.rawCsrfToken()))
                .isInstanceOf(com.delivery.web_bff.domain.session.SessionRejectedException.class);
        assertThat(store.byHash(material.session().sessionHash()).orElseThrow().revokedAt()).isNotNull();
    }

    @Test void sessionTransitionsSerializeWithPostgresRowLocks() throws Exception {
        var material = factory.create("old-access", "old-refresh", 21, "owner@example.test", "SHOP_OWNER", java.time.Instant.now());
        store.save(material.session());
        var acquired = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var rotating = workers.submit(() -> store.mutate(material.session().sessionHash(), session -> {
                acquired.countDown();
                try {
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test lock release timed out");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                session.rotate(tokens.protect("new-access"), tokens.protect("new-refresh"), tokens.keyVersion(), java.time.Instant.now());
            }));
            assertThat(acquired.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var revoking = workers.submit(() -> store.mutate(material.session().sessionHash(), session -> session.revoke(java.time.Instant.now())));
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
            boolean blocked = false;
            while (System.nanoTime() < deadline) {
                blocked = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and cardinality(pg_blocking_pids(pid)) > 0", Long.class) > 0;
                if (blocked) break;
                Thread.sleep(10);
            }
            assertThat(blocked).as("Postgres must block the second transition on the session row").isTrue();
            assertThat(revoking.isDone()).isFalse();
            release.countDown();
            assertThat(rotating.get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow().generation()).isEqualTo(2);
            assertThat(revoking.get(5, java.util.concurrent.TimeUnit.SECONDS).orElseThrow().generation()).isEqualTo(3);
            var persisted = store.byHash(material.session().sessionHash()).orElseThrow();
            assertThat(persisted.revokedAt()).isNotNull();
            assertThat(tokens.reveal(persisted.accessTokenCipher())).isEqualTo("new-access");
        } finally {
            release.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test void productionApplicationUsesOnlyInfrastructureSessionsAndActualUseCases() {
        assertThat(context.getBeansOfType(org.springframework.data.jpa.repository.JpaRepository.class)).hasSize(1);
        assertThat(context.getBean(com.delivery.web_bff.application.api.UseCases.Refresh.class))
                .isInstanceOf(com.delivery.web_bff.session.RefreshApplicationService.class);
        assertThat(context.getBean(com.delivery.web_bff.application.api.UseCases.Logout.class))
                .isInstanceOf(com.delivery.web_bff.session.LogoutApplicationService.class);
        assertThat(context.getBean(com.delivery.web_bff.application.api.UseCases.ProxyForwarding.class))
                .isInstanceOf(com.delivery.web_bff.proxy.ProxyForwardingApplicationService.class);
    }

    HttpResponse<String> send(String method, String path, String body, String cookie, String csrf, String origin) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        if (csrf != null) request.header("X-CSRF-Token", csrf);
        if (origin != null) request.header("Origin", origin);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration static class AuthFixture {
        @Bean @Primary AuthStub authStub() { return new AuthStub(); }
    }
    static final class AuthStub implements Ports.Authentication {
        final AtomicInteger refreshes = new AtomicInteger(), logouts = new AtomicInteger();
        public Ports.AuthenticatedTokens login(Ports.LoginCommand command) { return tokens(0); }
        public Ports.AuthenticatedTokens refresh(String refresh) {
            assertThat(refresh).isEqualTo("fixture-refresh-0");
            return tokens(refreshes.incrementAndGet());
        }
        public void logout(String refresh) { assertThat(refresh).isEqualTo("fixture-refresh-1"); logouts.incrementAndGet(); }
        private Ports.AuthenticatedTokens tokens(int generation) {
            return new Ports.AuthenticatedTokens("fixture-access-" + generation, "fixture-refresh-" + generation,
                    21, "owner@example.test", "SHOP_OWNER");
        }
    }
}
