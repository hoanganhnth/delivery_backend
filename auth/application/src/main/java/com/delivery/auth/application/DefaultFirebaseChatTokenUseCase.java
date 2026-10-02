package com.delivery.auth.application;

import com.delivery.auth.application.api.FirebaseChatTokenPort;
import com.delivery.auth.application.api.FirebaseChatTokenUseCase;
import com.delivery.auth.domain.policy.FirebaseChatUnavailable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DefaultFirebaseChatTokenUseCase implements FirebaseChatTokenUseCase {
    private final FirebaseChatTokenPort issuer;
    private final boolean enabled;

    public DefaultFirebaseChatTokenUseCase(FirebaseChatTokenPort issuer, boolean enabled) {
        this.issuer = issuer;
        this.enabled = enabled;
    }

    @Override public Result issue(Actor actor) {
        if (!enabled) throw new FirebaseChatUnavailable("Firebase support chat is disabled");
        if (actor == null || actor.principalId() == null || actor.principalId() <= 0) {
            throw new FirebaseChatUnavailable("Authenticated principal is missing");
        }
        if (!issuer.available()) {
            throw new FirebaseChatUnavailable("Firebase support chat credentials are unavailable");
        }
        String role = actor.roles().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT))
                .findFirst()
                .orElseThrow(() -> new FirebaseChatUnavailable("Authenticated role is missing"));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("principalId", String.valueOf(actor.principalId()));
        claims.put("role", role);
        claims.put("supportAgent", actor.supportAgent());
        String token = issuer.issue(String.valueOf(actor.principalId()), claims);
        return new Result(token, 3_600L, actor.principalId(), role);
    }
}
