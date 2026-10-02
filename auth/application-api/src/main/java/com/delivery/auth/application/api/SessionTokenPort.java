package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;

public interface SessionTokenPort {
    String issueAccessToken(AuthAccount account);
    String issueRefreshToken(AuthAccount account, String tokenFamilyId);
}
