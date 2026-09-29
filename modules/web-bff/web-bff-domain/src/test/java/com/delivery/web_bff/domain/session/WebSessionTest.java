package com.delivery.web_bff.domain.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WebSessionTest {
    @Test void lifecycleAndCsrfAreFailClosed() {
        TokenProtection p = new TokenProtection() { public String keyVersion(){return "v1";} public String protect(String v){return "enc:"+v;} public String reveal(String v){return v.substring(4);} };
        SessionMaterial m = new SessionFactory(p, size -> new byte[size], java.time.Duration.ofDays(1)).create("a", "r", 7, "e", "USER", Instant.EPOCH);
        assertTrue(m.session().isActive(Instant.EPOCH)); assertTrue(m.session().verifiesCsrf(m.rawCsrfToken())); assertFalse(m.session().verifiesCsrf("wrong"));
        assertTrue(m.session().claimRefresh(1, Instant.EPOCH, Instant.ofEpochSecond(30))); assertFalse(m.session().claimRefresh(1, Instant.EPOCH, Instant.ofEpochSecond(30)));
        m.session().rotate("a2", "r2", "v1", Instant.EPOCH); assertEquals(2, m.session().generation());
        m.session().revoke(Instant.EPOCH); assertFalse(m.session().isActive(Instant.EPOCH));
    }

    @Test void securityFactoryRehydrationAndInvalidStatesAreCovered() {
        assertThrows(IllegalArgumentException.class, () -> Security.hash(null));
        assertThrows(IllegalArgumentException.class, () -> Security.hash(" "));
        assertTrue(Security.constantTimeEquals("abc", "abc"));
        assertFalse(Security.constantTimeEquals("abc", "abd"));
        assertFalse(Security.constantTimeEquals(null, "abc"));
        assertFalse(Security.constantTimeEquals("abc", null));

        TokenProtection protection = new TokenProtection() {
            public String keyVersion() { return "v1"; }
            public String protect(String value) { return "enc:" + value; }
            public String reveal(String value) { return value.substring(4); }
        };
        assertThrows(IllegalArgumentException.class, () -> new SessionFactory(null, size -> new byte[size], java.time.Duration.ofDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new SessionFactory(protection, null, java.time.Duration.ofDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new SessionFactory(protection, size -> new byte[size], null));
        assertThrows(IllegalArgumentException.class, () -> new SessionFactory(protection, size -> new byte[size], java.time.Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SessionFactory(protection, size -> new byte[size], java.time.Duration.ofSeconds(-1)));

        WebSession rehydrated = WebSession.rehydrate("hash", "a", "r", "v1", 7, "e", "USER", "csrf", 2,
                Instant.ofEpochSecond(10), Instant.EPOCH, Instant.ofEpochSecond(5), Instant.ofEpochSecond(6), Instant.ofEpochSecond(7));
        assertEquals(Instant.ofEpochSecond(7), rehydrated.updatedAt());
        assertFalse(rehydrated.isActive(Instant.EPOCH));
        assertEquals("hash", rehydrated.sessionHash()); assertEquals("a", rehydrated.accessTokenCipher());
        assertEquals("r", rehydrated.refreshTokenCipher()); assertEquals("v1", rehydrated.encryptionKeyVersion());
        assertEquals(7, rehydrated.principalId()); assertEquals("e", rehydrated.email()); assertEquals("USER", rehydrated.role());
        assertEquals("csrf", rehydrated.csrfHash()); assertEquals(2, rehydrated.generation());
        assertEquals(Instant.ofEpochSecond(10), rehydrated.expiresAt()); assertEquals(Instant.EPOCH, rehydrated.createdAt());
        assertEquals(Instant.ofEpochSecond(5), rehydrated.revokedAt()); assertEquals(Instant.ofEpochSecond(6), rehydrated.refreshClaimedUntil());
        assertEquals(Instant.EPOCH, WebSession.rehydrate("hash", "a", "r", "v1", 7, "e", "USER", "csrf", 1,
                Instant.ofEpochSecond(10), Instant.EPOCH, null, null, null).updatedAt());
        assertThrows(IllegalArgumentException.class, () -> rehydrated.rotate(null, "r", "v1", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> rehydrated.rotate("a", null, "v1", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> rehydrated.rotate("a", "r", null, Instant.EPOCH));
        assertFalse(rehydrated.canClaimRefresh(2, Instant.EPOCH));

        assertThrows(IllegalArgumentException.class, () -> new WebSession(null, "a", "r", "v1", 1, "e", "u", "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession(" ", "a", "r", "v1", 1, "e", "u", "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", null, "r", "v1", 1, "e", "u", "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", null, "v1", 1, "e", "u", "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", null, 1, "e", "u", "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", "v1", 1, null, "u", "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", "v1", 1, "e", null, "c", 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", "v1", 1, "e", "u", null, 1, Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", "v1", 1, "e", "u", "c", 1, null, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", "v1", 1, "e", "u", "c", 1, Instant.EPOCH, null));
        assertThrows(IllegalArgumentException.class, () -> new WebSession("h", "a", "r", "v1", 1, "e", "u", "c", 0, Instant.EPOCH, Instant.EPOCH));
        assertNotNull(new SessionRejectedException("x"));
        assertNotNull(new AuthenticationRejectedException("x"));
        assertNotNull(new RefreshInProgressException("x"));
        assertNotNull(new ApiProxyRejectedException("x"));
    }

    @Test void refreshClaimLeaseAndRevokeBranchesAreCovered() {
        WebSession session = new WebSession("hash", "a", "r", "v1", 7, "e", "USER", Security.hash("csrf"), 1,
                Instant.ofEpochSecond(100), Instant.EPOCH);
        assertTrue(session.isActive(Instant.EPOCH));
        assertTrue(session.verifiesCsrf("csrf"));
        assertFalse(session.verifiesCsrf(null));
        assertFalse(session.verifiesCsrf("wrong"));
        assertFalse(session.canClaimRefresh(2, Instant.EPOCH));
        assertTrue(session.canClaimRefresh(1, Instant.EPOCH));
        assertTrue(session.claimRefresh(1, Instant.EPOCH, Instant.ofEpochSecond(10)));
        assertFalse(session.canClaimRefresh(1, Instant.EPOCH));
        assertTrue(session.canClaimRefresh(1, Instant.ofEpochSecond(10)));
        assertTrue(session.claimRefresh(1, Instant.ofEpochSecond(10), Instant.ofEpochSecond(20)));
        assertFalse(session.claimRefresh(1, Instant.EPOCH, Instant.ofEpochSecond(10)));
        session.revoke(Instant.EPOCH);
        assertFalse(session.canClaimRefresh(2, Instant.EPOCH));
        assertFalse(session.claimRefresh(2, Instant.EPOCH, Instant.ofEpochSecond(10)));
    }
}
