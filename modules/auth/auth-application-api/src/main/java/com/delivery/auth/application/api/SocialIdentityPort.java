package com.delivery.auth.application.api;

import java.util.Optional;

/** Provider verification adapter boundary for social login. */
public interface SocialIdentityPort {

    Optional<VerifiedSocialIdentity> verify(String provider, String providerToken);

    record VerifiedSocialIdentity(String provider, String email, boolean emailVerified) {
    }
}
