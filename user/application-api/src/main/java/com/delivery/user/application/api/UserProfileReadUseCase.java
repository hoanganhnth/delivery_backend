package com.delivery.user.application.api;

import java.util.List;

/** Application boundary for profile reads and administrative summaries. */
public interface UserProfileReadUseCase {

    UserProfileResult byAuthId(Long authId);

    UserProfileResult byPrincipalId(Long principalId);

    UserStatisticsResult statistics();

    List<UserProfileResult> all();
}
