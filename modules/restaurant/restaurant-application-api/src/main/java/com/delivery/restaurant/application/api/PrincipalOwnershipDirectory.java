package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.PrincipalOwnershipFacts;
import java.util.Optional;

public interface PrincipalOwnershipDirectory {

    Optional<PrincipalOwnershipFacts> findByPrincipalId(long principalId);
}
