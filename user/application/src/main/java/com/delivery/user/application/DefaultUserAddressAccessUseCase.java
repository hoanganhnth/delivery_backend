package com.delivery.user.application;

import com.delivery.user.application.api.*;
import java.util.Objects;

/** Customer address ownership; administrators may access all profiles. */
public final class DefaultUserAddressAccessUseCase implements UserAddressAccessUseCase {
    private final UserProfileReadUseCase profiles;

    public DefaultUserAddressAccessUseCase(UserProfileReadUseCase profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public boolean canAccess(Long ownerId, UserActor actor) {
        if (actor == null) return false;
        if (actor.admin()) return true;
        if (!actor.customer() || actor.principalId() == null || ownerId == null) return false;
        return ownerId.equals(profiles.byPrincipalId(actor.principalId()).id());
    }
}
