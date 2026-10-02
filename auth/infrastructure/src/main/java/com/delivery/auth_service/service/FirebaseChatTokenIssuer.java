package com.delivery.auth_service.service;

import com.google.firebase.auth.FirebaseAuthException;

import java.util.Map;

@FunctionalInterface
public interface FirebaseChatTokenIssuer {
    String createCustomToken(String uid, Map<String, Object> claims) throws FirebaseAuthException;
}
