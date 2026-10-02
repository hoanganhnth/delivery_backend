package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemManagementReadUseCase;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadPort;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultMenuItemManagementReadUseCaseTest {

    private final RecordingReadPort port = new RecordingReadPort();
    private final MenuItemManagementReadUseCase managementReads =
            new DefaultMenuItemManagementReadUseCase(port);

    @Test
    void adminReadIncludesEveryMenuStatusAndArchivedParentHistory() {
        MenuItemPageSlice rows = new MenuItemPageSlice(List.of(
                snapshot(1L, MenuItemStatus.AVAILABLE),
                snapshot(2L, MenuItemStatus.SOLD_OUT),
                snapshot(3L, MenuItemStatus.DISCONTINUED),
                snapshot(4L, MenuItemStatus.ARCHIVED)), 0, 100, 4, 1, false);
        port.result = Optional.of(rows);

        assertThat(managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.ADMIN, true, 0, 100)))
                .containsSame(rows);
        assertThat(port.lastQuery.actorRole()).isEqualTo(RestaurantActorRole.ADMIN);
        assertThat(port.lastQuery.size()).isEqualTo(100);
        assertThat(port.managementIncludesAllStatuses).isTrue();
        assertThat(port.managementIncludesArchivedParents).isTrue();
    }

    @Test
    void ownerReadUsesPrincipalBeforeLegacyIdentityAndAllowsLegacyFallbackOnlyWhenOff() {
        port.result = Optional.of(new MenuItemPageSlice(List.of(), 0, 24, 0, 0, false));

        assertThat(managementReads.read(new MenuItemManagementQuery(
                null, 101L, 7L, RestaurantActorRole.SHOP_OWNER, false, 0, 24)))
                .isPresent();
        assertThat(port.lastQuery.principalId()).isEqualTo(101L);
        assertThat(port.lastQuery.legacyUserId()).isEqualTo(7L);
        assertThat(port.lastQuery.principalOwnershipEnforced()).isFalse();
        assertThat(port.ownerPrincipalPrecedence).isTrue();

        managementReads.read(new MenuItemManagementQuery(
                null, 101L, 7L, RestaurantActorRole.SHOP_OWNER, true, 0, 24));
        assertThat(port.lastQuery.principalOwnershipEnforced()).isTrue();
    }

    @Test
    void specificRestaurantMissingRemainsEmptyAndForeignOwnerRemainsForbidden() {
        port.result = Optional.empty();
        assertThat(managementReads.read(new MenuItemManagementQuery(
                404L, 101L, 7L, RestaurantActorRole.SHOP_OWNER, true, 0, 24)))
                .isEmpty();

        port.failure = new ManagementAccessException(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT);
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                99L, 101L, 7L, RestaurantActorRole.SHOP_OWNER, true, 0, 24)))
                .isInstanceOf(ManagementAccessException.class)
                .extracting("failure")
                .isEqualTo(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT);
    }

    @Test
    void managementListAndPageKeepTheirDistinctLimits() {
        port.result = Optional.of(new MenuItemPageSlice(List.of(), 0, 100, 0, 0, false));
        managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.ADMIN, true, 0, 100));
        assertThat(port.lastQuery.size()).isEqualTo(100);

        managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.ADMIN, true, 2, 24));
        assertThat(port.lastQuery.page()).isEqualTo(2);
        assertThat(port.lastQuery.size()).isEqualTo(24);
    }

    @Test
    void managementReadRejectsSizeOutsideOneToOneHundredBeforeReadPort() {
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.ADMIN, true, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.ADMIN, true, 0, 101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(port.calls).isZero();
    }

    @Test
    void missingOrUnsupportedActorAndInvalidPageNeverReachReadPort() {
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                null, null, 7L, RestaurantActorRole.SHOP_OWNER, false, 0, 24)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                null, 1L, null, RestaurantActorRole.SHOP_OWNER, false, 0, 24)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.OTHER, false, 0, 24)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                null, 1L, 1L, RestaurantActorRole.ADMIN, true, -1, 24)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(port.calls).isZero();
    }

    @Test
    void nonPositivePrincipalAndLegacyIdsNeverReachReadPort() {
        for (Long invalidPrincipalId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                    null, invalidPrincipalId, 7L, RestaurantActorRole.SHOP_OWNER, false, 0, 24)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        for (Long invalidLegacyId : new Long[] {0L, -1L}) {
            assertThatThrownBy(() -> managementReads.read(new MenuItemManagementQuery(
                    null, 101L, invalidLegacyId, RestaurantActorRole.SHOP_OWNER, false, 0, 24)))
                    .isInstanceOf(ManagementAccessException.class);
        }
        assertThat(port.calls).isZero();
    }

    private MenuItemSnapshot snapshot(Long id, MenuItemStatus status) {
        return new MenuItemSnapshot(id, 42L, "Item " + id, "Description",
                new BigDecimal("50000.00"), status, null, null, "item.png", 1L);
    }

    private static final class RecordingReadPort implements MenuItemReadPort {
        private Optional<MenuItemPageSlice> result = Optional.empty();
        private RuntimeException failure;
        private MenuItemManagementQuery lastQuery;
        private int calls;
        private boolean managementIncludesAllStatuses;
        private boolean managementIncludesArchivedParents;
        private boolean ownerPrincipalPrecedence;

        @Override
        public List<MenuItemSnapshot> findPublicByRestaurant(Long restaurantId, int limit) {
            return List.of();
        }

        @Override
        public MenuItemPageSlice pagePublicByRestaurant(Long restaurantId, int page, int size) {
            return new MenuItemPageSlice(List.of(), page, size, 0, 0, false);
        }

        @Override
        public Optional<MenuItemPageSlice> findManaged(MenuItemManagementQuery query) {
            calls++;
            lastQuery = query;
            managementIncludesAllStatuses = true;
            managementIncludesArchivedParents = true;
            ownerPrincipalPrecedence = query.principalId() != null;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
