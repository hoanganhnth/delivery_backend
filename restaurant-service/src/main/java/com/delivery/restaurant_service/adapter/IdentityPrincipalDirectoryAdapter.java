package com.delivery.restaurant_service.adapter;

import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityRole;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.domain.ownership.PrincipalOwnershipFacts;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class IdentityPrincipalDirectoryAdapter implements PrincipalOwnershipDirectory {

    private final Supplier<IdentityPrincipalClient> clientSupplier;

    public IdentityPrincipalDirectoryAdapter(IdentityPrincipalClient client) {
        this(() -> client);
    }

    public IdentityPrincipalDirectoryAdapter(Supplier<IdentityPrincipalClient> clientSupplier) {
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
    }

    @Override
    public Optional<PrincipalOwnershipFacts> findByPrincipalId(long principalId) {
        return clientSupplier.get().findByPrincipalId(principalId)
                .map(principal -> new PrincipalOwnershipFacts(
                        principal.principalId(),
                        principal.role() == IdentityRole.SHOP_OWNER,
                        principal.lifecycleStatus() == IdentityLifecycleStatus.ACTIVE));
    }
}
