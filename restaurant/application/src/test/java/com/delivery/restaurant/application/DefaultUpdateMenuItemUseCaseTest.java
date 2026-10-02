package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.application.api.MenuItemStoredFacts;
import com.delivery.restaurant.application.api.MenuItemUpdateDecision;
import com.delivery.restaurant.application.api.MenuItemUpdatePort;
import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultUpdateMenuItemUseCaseTest {

    private final RecordingUpdatePort port = new RecordingUpdatePort();
    private final DefaultUpdateMenuItemUseCase useCase = new DefaultUpdateMenuItemUseCase(
            port, new DefaultRestaurantManagementAccessUseCase());

    @Test
    void ownerUpdatePlansEveryPatchFieldWithoutChangingParent() {
        port.stored = stored(9L, 42L, 7L, 700L);

        assertThat(useCase.update(command(9L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                "Bun", "Updated", new BigDecimal("65000.00"), MenuItemStatus.SOLD_OUT,
                "bun.png", 999L))).isPresent();
        assertThat(port.plan.menuItemId()).isEqualTo(9L);
        assertThat(port.plan.restaurantId()).isEqualTo(42L);
        assertThat(port.plan.restaurantId()).isNotEqualTo(999L);
        assertThat(port.plan.name()).isEqualTo("Bun");
        assertThat(port.plan.description()).isEqualTo("Updated");
        assertThat(port.plan.price()).isEqualByComparingTo("65000.00");
        assertThat(port.plan.status()).isEqualTo(MenuItemStatus.SOLD_OUT);
        assertThat(port.plan.image()).isEqualTo("bun.png");
    }

    @Test
    void adminMayUpdateAcrossRestaurantOwnership() {
        port.stored = stored(9L, 42L, 99L, 700L);

        assertThat(useCase.update(command(9L, 1L, 1L, RestaurantActorRole.ADMIN, true,
                null, null, null, null, null, null))).isPresent();
        assertThat(port.plan.restaurantId()).isEqualTo(42L);
    }

    @Test
    void nullPatchFieldsPreserveStoredValuesAndEmptyPatchIsValid() {
        port.stored = stored(9L, 42L, 7L, 700L);

        assertThat(useCase.update(command(9L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                null, null, null, null, null, null))).isPresent();
        assertThat(port.plan.name()).isEqualTo("Old");
        assertThat(port.plan.description()).isEqualTo("Original");
        assertThat(port.plan.price()).isEqualByComparingTo("50000.00");
        assertThat(port.plan.status()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(port.plan.image()).isEqualTo("old.png");
    }

    @Test
    void missingMenuReturnsEmptyWithoutMutationPlan() {
        port.stored = null;

        assertThat(useCase.update(command(404L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                "New", null, null, null, null, null))).isEmpty();
        assertThat(port.plan).isNull();
    }

    @Test
    void foreignOwnerDoesNotProduceMutationPlan() {
        port.stored = stored(9L, 42L, 99L, 700L);

        assertThatThrownBy(() -> useCase.update(command(9L, 7L, 700L, RestaurantActorRole.SHOP_OWNER,
                true, "New", null, null, null, null, null)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.plan).isNull();
    }

    @Test
    void legacyFallbackClaimsPrincipalWhenEnforcementIsOff() {
        port.stored = stored(9L, 42L, null, 700L);

        assertThat(useCase.update(command(9L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, false,
                "New", null, null, null, null, null))).isPresent();
        assertThat(port.plan.restaurantOwnerPrincipalId()).isEqualTo(101L);
    }

    @Test
    void matchingPrincipalWinsOverLegacyIdentityWhenEnforcementIsOff() {
        port.stored = stored(9L, 42L, 101L, 700L);

        assertThat(useCase.update(command(9L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, false,
                "New", null, null, null, null, null)))
                .isPresent()
                .get()
                .extracting(MenuItemUpdateResult::claimedLegacyOwnership)
                .isEqualTo(false);
        assertThat(port.plan.restaurantOwnerPrincipalId()).isEqualTo(101L);
    }

    @Test
    void legacyRowIsRejectedWhenEnforcementIsOn() {
        port.stored = stored(9L, 42L, null, 700L);

        assertThatThrownBy(() -> useCase.update(command(9L, 101L, 700L, RestaurantActorRole.SHOP_OWNER,
                true, "New", null, null, null, null, null)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.plan).isNull();
    }

    @Test
    void nonPositiveIdsNeverReachUpdatePort() {
        for (Long invalidMenuItemId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> useCase.update(command(invalidMenuItemId, 101L, 700L,
                    RestaurantActorRole.SHOP_OWNER, false, null, null, null, null, null, null)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        for (Long invalidPrincipalId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> useCase.update(command(9L, invalidPrincipalId, 700L,
                    RestaurantActorRole.SHOP_OWNER, false, null, null, null, null, null, null)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        for (Long invalidLegacyId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> useCase.update(command(9L, 101L, invalidLegacyId,
                    RestaurantActorRole.SHOP_OWNER, false, null, null, null, null, null, null)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        assertThat(port.invocations).isZero();
    }

    @Test
    void missingOrUnsupportedActorNeverReachesUpdatePort() {
        assertThatThrownBy(() -> useCase.update(command(9L, null, 700L, RestaurantActorRole.SHOP_OWNER,
                true, null, null, null, null, null, null)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> useCase.update(command(9L, 7L, null, RestaurantActorRole.SHOP_OWNER,
                true, null, null, null, null, null, null)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> useCase.update(command(9L, 7L, 700L, RestaurantActorRole.OTHER,
                true, null, null, null, null, null, null)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.invocations).isZero();
    }

    private UpdateMenuItemCommand command(Long menuId, Long principalId, Long legacyId,
            RestaurantActorRole role, boolean enforced, String name, String description,
            BigDecimal price, MenuItemStatus status, String image, Long requestedRestaurantId) {
        return new UpdateMenuItemCommand(menuId, principalId, legacyId, role, enforced,
                name, description, price, status, image, requestedRestaurantId);
    }

    private MenuItemStoredFacts stored(Long menuId, Long restaurantId, Long owner, Long creator) {
        return new MenuItemStoredFacts(menuId, restaurantId, owner, creator, "Old", "Original",
                new BigDecimal("50000.00"), MenuItemStatus.AVAILABLE, "old.png",
                LocalDateTime.MIN, LocalDateTime.MIN, 3L);
    }

    private static final class RecordingUpdatePort implements MenuItemUpdatePort {
        private MenuItemStoredFacts stored;
        private MenuItemMutationPlan plan;
        private int invocations;

        @Override
        public Optional<MenuItemUpdateResult> update(UpdateMenuItemCommand command,
                MenuItemUpdateDecision decision) {
            invocations++;
            if (stored == null) {
                return Optional.empty();
            }
            plan = decision.decide(stored);
            return Optional.of(new MenuItemUpdateResult(
                    new MenuItemSnapshot(plan.menuItemId(), plan.restaurantId(), plan.name(),
                            plan.description(), plan.price(), plan.status(), stored.createdAt(),
                            stored.updatedAt(), plan.image(), stored.version()),
                    stored.restaurantOwnerPrincipalId() == null
                            && plan.restaurantOwnerPrincipalId() != null));
        }
    }
}
