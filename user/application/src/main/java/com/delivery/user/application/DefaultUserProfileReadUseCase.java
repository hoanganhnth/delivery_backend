package com.delivery.user.application;

import com.delivery.user.application.api.UserProfileReadPort;
import com.delivery.user.application.api.UserProfileReadUseCase;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserStatisticsResult;
import java.util.List;
import java.util.Objects;

/** Coordinates profile reads through the persistence boundary. */
public final class DefaultUserProfileReadUseCase implements UserProfileReadUseCase {

    private final UserProfileReadPort profileReadPort;

    public DefaultUserProfileReadUseCase(UserProfileReadPort profileReadPort) {
        this.profileReadPort = Objects.requireNonNull(profileReadPort, "profileReadPort");
    }

    @Override
    public UserProfileResult byAuthId(Long authId) {
        return profileReadPort.byAuthId(Objects.requireNonNull(authId, "authId"));
    }

    @Override
    public UserProfileResult byPrincipalId(Long principalId) {
        return profileReadPort.byPrincipalId(Objects.requireNonNull(principalId, "principalId"));
    }

    @Override
    public UserStatisticsResult statistics() {
        return profileReadPort.statistics();
    }

    @Override
    public List<UserProfileResult> all() {
        return profileReadPort.all();
    }
}
