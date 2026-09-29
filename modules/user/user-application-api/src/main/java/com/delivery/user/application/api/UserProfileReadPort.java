package com.delivery.user.application.api;

import java.util.List;

/** Persistence boundary for profile reads and administrative summaries. */
public interface UserProfileReadPort {

    UserProfileResult byAuthId(Long authId);

    UserProfileResult byPrincipalId(Long principalId);

    UserStatisticsResult statistics();

    List<UserProfileResult> all();
}
