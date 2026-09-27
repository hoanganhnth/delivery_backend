package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.MenuItemCreateDecision;
import com.delivery.restaurant.application.api.MenuItemCreateResult;
import com.delivery.restaurant.application.api.MenuItemCreationPort;
import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultCreateMenuItemUseCaseTest {

    private final RecordingCreationPort port = new RecordingCreationPort();
    private final RestaurantManagementAccessUseCase access =
            new DefaultRestaurantManagementAccessUseCase();
    private final DefaultCreateMenuItemUseCase useCase =
            new DefaultCreateMenuItemUseCase(port, access);

    @Test
    void ownerCreateUsesParentOwnershipAndAvailableDefaults() {
        port.facts = new RestaurantManagementFacts(7L, 700L);

        assertThat(useCase.create(command(42L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isPresent();
        assertThat(port.plan).isNotNull();
        assertThat(port.plan.restaurantId()).isEqualTo(42L);
        assertThat(port.plan.restaurantOwnerPrincipalId()).isEqualTo(7L);
        assertThat(port.plan.name()).isEqualTo("Pho");
        assertThat(port.plan.description()).isEqualTo("Classic");
        assertThat(port.plan.price()).isEqualByComparingTo("55000.00");
        assertThat(port.plan.status()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(port.plan.image()).isEqualTo("pho.png");
    }

    @Test
    void adminMayCreateForAnotherRestaurant() {
        port.facts = new RestaurantManagementFacts(99L, 700L);

        assertThat(useCase.create(command(42L, 1L, 1L, RestaurantActorRole.ADMIN, true)))
                .isPresent();
        assertThat(port.plan.restaurantId()).isEqualTo(42L);
        assertThat(port.plan.restaurantOwnerPrincipalId()).isEqualTo(99L);
    }

    @Test
    void foreignOwnerDoesNotProduceMutationPlan() {
        port.facts = new RestaurantManagementFacts(99L, 700L);

        assertThatThrownBy(() -> useCase.create(
                command(42L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.plan).isNull();
    }

    @Test
    void missingOrUnsupportedActorNeverReachesPersistencePort() {
        assertThatThrownBy(() -> useCase.create(
                command(42L, null, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> useCase.create(
                command(42L, 7L, null, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> useCase.create(
                command(42L, 7L, 700L, RestaurantActorRole.OTHER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.invocations).isZero();
    }

    @Test
    void missingRestaurantReturnsEmptyWithoutPlanning() {
        port.facts = null;

        assertThat(useCase.create(command(404L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isEmpty();
        assertThat(port.plan).isNull();
    }

    @Test
    void legacyOwnerCreateClaimsCurrentPrincipalWhenEnforcementIsOff() {
        port.facts = new RestaurantManagementFacts(null, 700L);

        assertThat(useCase.create(command(42L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, false)))
                .isPresent();
        assertThat(port.plan.restaurantOwnerPrincipalId()).isEqualTo(101L);
    }

    @Test
    void matchingPrincipalWinsOverLegacyIdentityWhenEnforcementIsOff() {
        port.facts = new RestaurantManagementFacts(101L, 700L);

        assertThat(useCase.create(command(42L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, false)))
                .isPresent()
                .get()
                .extracting(MenuItemCreateResult::claimedLegacyOwnership)
                .isEqualTo(false);
        assertThat(port.plan.restaurantOwnerPrincipalId()).isEqualTo(101L);
    }

    @Test
    void legacyOwnerIsRejectedWhenPrincipalEnforcementIsOn() {
        port.facts = new RestaurantManagementFacts(null, 700L);

        assertThatThrownBy(() -> useCase.create(
                command(42L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.plan).isNull();
    }

    @Test
    void nonPositiveIdsNeverReachPersistencePort() {
        for (Long invalidRestaurantId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> useCase.create(
                    command(invalidRestaurantId, 101L, 700L, RestaurantActorRole.SHOP_OWNER, false)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        for (Long invalidPrincipalId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> useCase.create(
                    command(42L, invalidPrincipalId, 700L, RestaurantActorRole.SHOP_OWNER, false)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        for (Long invalidLegacyId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> useCase.create(
                    command(42L, 101L, invalidLegacyId, RestaurantActorRole.SHOP_OWNER, false)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        assertThat(port.invocations).isZero();
    }

    private CreateMenuItemCommand command(Long restaurantId, Long principalId, Long legacyId,
            RestaurantActorRole role, boolean enforced) {
        return new CreateMenuItemCommand(restaurantId, principalId, legacyId, role, enforced,
                "Pho", "Classic", new BigDecimal("55000.00"), "pho.png");
    }

    private static final class RecordingCreationPort implements MenuItemCreationPort {
        private RestaurantManagementFacts facts;
        private MenuItemMutationPlan plan;
        private int invocations;

        @Override
        public Optional<MenuItemCreateResult> create(CreateMenuItemCommand command,
                MenuItemCreateDecision decision) {
            invocations++;
            if (facts == null) {
                return Optional.empty();
            }
            plan = decision.decide(facts);
            return Optional.of(new MenuItemCreateResult(
                    new MenuItemSnapshot(null, plan.restaurantId(), plan.name(), plan.description(),
                            plan.price(), plan.status(), null, null, plan.image(), null),
                    plan.restaurantOwnerPrincipalId() != null && facts.ownerPrincipalId() == null));
        }
    }
}
