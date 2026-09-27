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
}
