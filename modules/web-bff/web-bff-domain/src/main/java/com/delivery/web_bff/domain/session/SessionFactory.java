package com.delivery.web_bff.domain.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public final class SessionFactory {
    private final TokenProtection tokens; private final Randomness random; private final Duration maxAge;
    public SessionFactory(TokenProtection tokens, Randomness random, Duration maxAge) {
        if (tokens == null || random == null || maxAge == null || maxAge.isNegative() || maxAge.isZero()) throw new IllegalArgumentException("Invalid session factory");
        this.tokens = tokens; this.random = random; this.maxAge = maxAge;
    }
    public SessionMaterial create(String accessToken, String refreshToken, long principalId, String email, String role, Instant now) {
        String rawId = token(), csrf = token();
        return new SessionMaterial(rawId, csrf, new WebSession(Security.hash(rawId), tokens.protect(accessToken), tokens.protect(refreshToken),
                tokens.keyVersion(), principalId, email, role, Security.hash(csrf), 1, now.plus(maxAge), now));
    }
    private String token() { return Base64.getUrlEncoder().withoutPadding().encodeToString(random.bytes(32)); }
}
