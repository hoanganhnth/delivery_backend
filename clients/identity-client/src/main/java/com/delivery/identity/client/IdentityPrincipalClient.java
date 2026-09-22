package com.delivery.identity.client;

import com.delivery.identity.contracts.IdentityPrincipal;
import java.util.Optional;

public interface IdentityPrincipalClient {

    Optional<IdentityPrincipal> findByPrincipalId(long principalId);
}
