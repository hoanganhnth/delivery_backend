package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadPort;
import com.delivery.restaurant.application.api.MenuItemSnapshot;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultMenuItemReadUseCaseTest {

    private final RecordingReadPort port = new RecordingReadPort();
    private final DefaultMenuItemReadUseCase publicReads = new DefaultMenuItemReadUseCase(port);

    @Test
    void publicListUsesAvailableProjectionAndCapsAtOneHundred() {
        MenuItemSnapshot active = snapshot(1L, 42L, MenuItemStatus.AVAILABLE);
        MenuItemSnapshot pausedRestaurantItem = snapshot(2L, 42L, MenuItemStatus.AVAILABLE);
        port.publicItems = List.of(active, pausedRestaurantItem);

        assertThat(publicReads.listPublic(42L)).containsExactly(active, pausedRestaurantItem);
        assertThat(port.lastRestaurantId).isEqualTo(42L);
        assertThat(port.lastPublicLimit).isEqualTo(100);
    }

    @Test
    void publicProjectionDoesNotExposeNonAvailableOrArchivedParentRows() {
        MenuItemSnapshot available = snapshot(1L, 42L, MenuItemStatus.AVAILABLE);
        port.publicItems = List.of(available);

        assertThat(publicReads.listPublic(42L)).containsExactly(available);
        assertThat(port.publicQueryWasAvailableOnly).isTrue();
        assertThat(port.publicQueryExcludedArchivedParent).isTrue();
    }

    @Test
    void publicPagePreservesMetadataAndUsesRequestedPageAndSize() {
        MenuItemPageSlice page = new MenuItemPageSlice(
                List.of(snapshot(1L, 42L, MenuItemStatus.AVAILABLE)), 2, 24, 49, 3, true);
        port.publicPage = page;

        assertThat(publicReads.pagePublic(42L, 2, 24)).isSameAs(page);
        assertThat(port.lastPage).isEqualTo(2);
        assertThat(port.lastSize).isEqualTo(24);
    }

    @Test
    void publicPageRejectsInvalidBoundsBeforeReadPort() {
        assertThatThrownBy(() -> publicReads.pagePublic(42L, -1, 24))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicReads.pagePublic(42L, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicReads.pagePublic(42L, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(port.pageCalls).isZero();
    }

    private MenuItemSnapshot snapshot(Long id, Long restaurantId, MenuItemStatus status) {
        return new MenuItemSnapshot(id, restaurantId, "Item " + id, "Description",
                new BigDecimal("50000.00"), status, null, null, "item.png", 1L);
    }

    private static final class RecordingReadPort implements MenuItemReadPort {
        private List<MenuItemSnapshot> publicItems = List.of();
        private MenuItemPageSlice publicPage = new MenuItemPageSlice(List.of(), 0, 24, 0, 0, false);
        private Long lastRestaurantId;
        private int lastPublicLimit;
        private int lastPage;
        private int lastSize;
        private int pageCalls;
        private boolean publicQueryWasAvailableOnly;
        private boolean publicQueryExcludedArchivedParent;

        @Override
        public List<MenuItemSnapshot> findPublicByRestaurant(Long restaurantId, int limit) {
            lastRestaurantId = restaurantId;
            lastPublicLimit = limit;
            publicQueryWasAvailableOnly = true;
            publicQueryExcludedArchivedParent = true;
            return publicItems;
        }

        @Override
        public MenuItemPageSlice pagePublicByRestaurant(Long restaurantId, int page, int size) {
            pageCalls++;
            lastRestaurantId = restaurantId;
            lastPage = page;
            lastSize = size;
            return publicPage;
        }

        @Override
        public Optional<MenuItemPageSlice> findManaged(MenuItemManagementQuery query) {
            return Optional.empty();
        }
    }
}
