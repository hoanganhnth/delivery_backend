package com.delivery.auth_service.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.delivery.auth_service.exception.InvalidCredentialsException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Component
public class GoogleTokenVerifier {

    private final List<String> clientIds;

    public GoogleTokenVerifier(@Value("${google.oauth.client-ids:}") String configuredClientIds) {
        this.clientIds = Arrays.stream(configuredClientIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public GoogleIdToken.Payload verify(String rawToken) {
        if (clientIds.isEmpty()) {
            throw new InvalidCredentialsException("Google login is not configured");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(clientIds)
                    .build();
            GoogleIdToken idToken = verifier.verify(rawToken);
            if (idToken == null || idToken.getPayload() == null) {
                throw new InvalidCredentialsException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())
                    || payload.getEmail() == null
                    || payload.getEmail().isBlank()) {
                throw new InvalidCredentialsException("Google account email is not verified");
            }
            return payload;
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCredentialsException("Invalid Google ID token");
        }
    }
}
