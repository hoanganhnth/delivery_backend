package com.delivery.auth_service.service;

import com.delivery.auth.application.api.FirebaseChatTokenPort;
import com.delivery.auth.domain.policy.FirebaseChatUnavailable;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public final class FirebaseChatTokenAdapter implements FirebaseChatTokenPort {
    private final ObjectProvider<FirebaseChatTokenIssuer> issuers;
    public FirebaseChatTokenAdapter(ObjectProvider<FirebaseChatTokenIssuer> issuers) { this.issuers = issuers; }
    @Override public boolean available() { return issuers.getIfAvailable() != null; }
    @Override public String issue(String uid, Map<String, Object> claims) {
        try {
            return issuers.getObject().createCustomToken(uid, claims);
        } catch (FirebaseAuthException failure) {
            throw new FirebaseChatUnavailable("Firebase custom token could not be created", failure);
        }
    }
}
