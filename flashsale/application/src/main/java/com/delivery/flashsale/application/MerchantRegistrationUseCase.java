package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.OwnershipPort;
import java.util.function.Supplier;

/** Ownership precedes the existing service-validation/transaction boundary. */
public final class MerchantRegistrationUseCase {
    private final OwnershipPort ownership;
    public MerchantRegistrationUseCase(OwnershipPort ownership) { this.ownership = ownership; }
    public <I> I register(Long restaurantId, Long principalId, Long legacyId, Supplier<I> registration) {
        ownership.requireOwnedBy(restaurantId, principalId, legacyId);
        return registration.get();
    }
}
