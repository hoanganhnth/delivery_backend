package com.delivery.auth_service.application.port.in;

import com.delivery.identity.contracts.IdentityPrincipal;
import java.util.Optional;

/** Inbound application port for trusted internal principal lookup. */
public interface PrincipalLookupUseCase {
    Optional<IdentityPrincipal> findByPrincipalId(Long principalId);
}
