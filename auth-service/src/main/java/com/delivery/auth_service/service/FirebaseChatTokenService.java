package com.delivery.auth_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth_service.dto.FirebaseChatTokenResponse;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class FirebaseChatTokenService {

    private static final long TOKEN_TTL_SECONDS = 3_600L;

    private final ObjectProvider<FirebaseChatTokenIssuer> tokenIssuerProvider;
    private final boolean enabled;

    public FirebaseChatTokenService(
            ObjectProvider<FirebaseChatTokenIssuer> tokenIssuerProvider,
            @Value("${app.firebase.chat.enabled:false}") boolean enabled) {
        this.tokenIssuerProvider = tokenIssuerProvider;
        this.enabled = enabled;
    }

    public FirebaseChatTokenResponse issue(AuthenticatedActor actor) {
        if (!enabled) {
            throw new FirebaseChatUnavailableException("Firebase support chat is disabled");
        }
        if (actor == null || actor.getPrincipalId() == null || actor.getPrincipalId() <= 0) {
            throw new FirebaseChatUnavailableException("Authenticated principal is missing");
        }

        FirebaseChatTokenIssuer tokenIssuer = tokenIssuerProvider.getIfAvailable();
        if (tokenIssuer == null) {
            throw new FirebaseChatUnavailableException("Firebase support chat credentials are unavailable");
        }

        String role = actor.getRoles().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT))
                .findFirst()
                .orElseThrow(() -> new FirebaseChatUnavailableException("Authenticated role is missing"));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("principalId", String.valueOf(actor.getPrincipalId()));
        claims.put("role", role);
        claims.put("supportAgent", actor.isAdmin());

        try {
            String token = tokenIssuer.createCustomToken(String.valueOf(actor.getPrincipalId()), claims);
            return new FirebaseChatTokenResponse(
                    token,
                    TOKEN_TTL_SECONDS,
                    actor.getPrincipalId(),
                    role);
        } catch (FirebaseAuthException exception) {
            throw new FirebaseChatUnavailableException("Firebase custom token could not be created", exception);
        }
    }

    public static final class FirebaseChatUnavailableException extends RuntimeException {
        public FirebaseChatUnavailableException(String message) {
            super(message);
        }

        public FirebaseChatUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
