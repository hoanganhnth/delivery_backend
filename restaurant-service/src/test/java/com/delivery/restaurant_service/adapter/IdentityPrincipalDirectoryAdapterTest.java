package com.delivery.restaurant_service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.identity.client.IdentityPrincipalClient;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentityPrincipalDirectoryAdapterTest {

    @Test
    void mapsOnlyFactsNeededByRestaurantOwnership() {
        IdentityPrincipalClient client = principalId -> Optional.of(new IdentityPrincipal(
                principalId, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE));

        var facts = new IdentityPrincipalDirectoryAdapter(client)
                .findByPrincipalId(42L).orElseThrow();

        assertThat(facts.principalId()).isEqualTo(42L);
        assertThat(facts.shopOwner()).isTrue();
        assertThat(facts.active()).isTrue();
    }

    @Test
    void preservesMissingAndNonOwnerInactiveFacts() {
        assertThat(new IdentityPrincipalDirectoryAdapter(principalId -> Optional.empty())
                .findByPrincipalId(42L)).isEmpty();

        IdentityPrincipalClient client = principalId -> Optional.of(new IdentityPrincipal(
                principalId, IdentityRole.USER, IdentityLifecycleStatus.BLOCKED));
        var facts = new IdentityPrincipalDirectoryAdapter(client)
                .findByPrincipalId(42L).orElseThrow();
        assertThat(facts.shopOwner()).isFalse();
        assertThat(facts.active()).isFalse();
    }
}
