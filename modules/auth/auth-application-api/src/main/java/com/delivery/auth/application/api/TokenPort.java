package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.TokenValidity;

/** Token adapter boundary; JWT/JWKS details stay outside the domain modules. */
public interface TokenPort {

    TokenValidity inspect(String rawToken);

    String issueAccessToken(AuthAccount account);

    String issueRefreshToken(AuthAccount account, String tokenFamilyId);

    String issueProvisioningToken(AuthAccount account);
}
