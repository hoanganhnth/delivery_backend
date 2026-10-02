package com.delivery.auth_service.service;

import com.delivery.auth.application.api.SocialIdentityPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleSocialIdentityAdapter implements SocialIdentityPort {
    private final GoogleTokenVerifier verifier;
    @Override public Optional<VerifiedSocialIdentity> verify(String provider, String rawToken) {
        // The technical verifier already enforces Google's signature/audience and verified-email claim.
        var payload = verifier.verify(rawToken);
        return Optional.of(new VerifiedSocialIdentity("google", payload.getEmail(), true));
    }
}
