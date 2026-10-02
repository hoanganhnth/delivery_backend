package com.delivery.restaurant.infrastructure.identity;

import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityPrincipalDirectoryAdapterTest {
    @Test
    void mapsOnlyOwnershipFactsNeededByApplication() {
        var client = (com.delivery.identity.client.IdentityPrincipalClient) id -> Optional.of(
                new IdentityPrincipal(id, IdentityRole.SHOP_OWNER, IdentityLifecycleStatus.ACTIVE));

        var facts = new IdentityPrincipalDirectoryAdapter(client).findByPrincipalId(42L).orElseThrow();

        assertThat(facts.principalId()).isEqualTo(42L);
        assertThat(facts.shopOwner()).isTrue();
        assertThat(facts.active()).isTrue();
    }

    @Test
    void preservesAbsentAndIneligibleIdentityFacts() {
        assertThat(new IdentityPrincipalDirectoryAdapter(id -> Optional.empty())
                .findByPrincipalId(42L)).isEmpty();

        var facts = new IdentityPrincipalDirectoryAdapter(id -> Optional.of(
                new IdentityPrincipal(id, IdentityRole.USER, IdentityLifecycleStatus.BLOCKED)))
                .findByPrincipalId(42L).orElseThrow();

        assertThat(facts.shopOwner()).isFalse();
        assertThat(facts.active()).isFalse();
    }
}
