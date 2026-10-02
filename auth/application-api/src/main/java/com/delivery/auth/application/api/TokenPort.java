package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.TokenValidity;

/** Token adapter boundary; JWT/JWKS details stay outside the domain modules. */
public interface TokenPort extends ProvisioningTokenPort, SessionTokenPort {

    TokenValidity inspect(String rawToken);



}
