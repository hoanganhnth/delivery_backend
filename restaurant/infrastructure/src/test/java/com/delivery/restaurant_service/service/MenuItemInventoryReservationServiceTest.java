package com.delivery.restaurant_service.service;

import com.delivery.restaurant.application.api.InventoryReservationCommand;
import com.delivery.restaurant.application.api.InventoryReservationLineCommand;
import com.delivery.restaurant.application.DefaultMenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant.infrastructure.inventory.JpaInventoryAdapter;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.MenuItemInventory;
import com.delivery.restaurant_service.entity.MenuItemInventoryReservation;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemInventoryRepository;
import com.delivery.restaurant_service.repository.MenuItemInventoryReservationRepository;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MenuItemInventoryReservationServiceTest {

    @Mock MenuItemRepository menuItemRepository;
    @Mock MenuItemInventoryRepository inventoryRepository;
    @Mock MenuItemInventoryReservationRepository reservationRepository;

    private DefaultMenuItemInventoryUseCase service;
    private MenuItem item;
    private MenuItemInventory inventory;

    @BeforeEach
    void setUp() {
        service = new DefaultMenuItemInventoryUseCase(
                new JpaInventoryAdapter(menuItemRepository, inventoryRepository, reservationRepository),
                new RestaurantTransactionPort() {
                    public <T> T required(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T repeatableRead(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T readOnly(java.util.function.Supplier<T> operation) { return operation.get(); }
                }, Duration.ofMinutes(15), java.time.Clock.systemDefaultZone());
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        item = new MenuItem();
        item.setId(11L);
        item.setRestaurant(restaurant);
        item.setStatus(MenuItem.Status.AVAILABLE);
        inventory = new MenuItemInventory();
        inventory.setMenuItemId(11L);
        inventory.setOnHandQuantity(5);
        inventory.setReservedQuantity(0);
        inventory.setRevision(0L);
        lenient().when(inventoryRepository.findById(11L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.findById(any())).thenReturn(Optional.empty());
        when(reservationRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(menuItemRepository.findAllByIdForUpdate(any())).thenReturn(List.of(item));
        when(inventoryRepository.findAllByMenuItemIdInForUpdate(any())).thenReturn(List.of(inventory));
        lenient().when(reservationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reserveLocksAllLinesAndDoesNotBackorder() {
        var response = service.reserve(request(UUID.randomUUID(), 2));

        assertThat(response.state()).isEqualTo("RESERVED");
        assertThat(response.items()).singleElement().satisfies(line -> {
            assertThat(line.menuItemId()).isEqualTo(11L);
            assertThat(line.quantity()).isEqualTo(2);
        });
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        verify(reservationRepository).saveAndFlush(any());
    }

    @Test
    void insufficientCapacityFailsBeforeAnyReservationWrite() {
        assertThatThrownBy(() -> service.reserve(request(UUID.randomUUID(), 6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient inventory");

        assertThat(inventory.getReservedQuantity()).isZero();
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void exactReplayReturnsExistingReservationButContradictoryReplayFails() {
        UUID reservationId = UUID.randomUUID();
        var first = service.reserve(request(reservationId, 2));
        MenuItemInventoryReservation stored = reservationRepositoryArgument();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(stored));
        when(reservationRepository.findByOrderId(101L)).thenReturn(Optional.of(stored));

        assertThat(service.reserve(request(reservationId, 2)).state()).isEqualTo(first.state());
        assertThatThrownBy(() -> service.reserve(request(reservationId, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replay payload");
    }

    @Test
    void commitConsumesHeldCapacityAndReleaseCompensatesCommittedSale() {
        UUID reservationId = UUID.randomUUID();
        service.reserve(request(reservationId, 2));
        MenuItemInventoryReservation stored = reservationRepositoryArgument();
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(stored));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(stored));

        assertThat(service.commit(reservationId, 101L).state()).isEqualTo("COMMITTED");
        assertThat(inventory.getOnHandQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isZero();

        assertThat(service.release(reservationId, 101L).state()).isEqualTo("RELEASED");
        assertThat(inventory.getOnHandQuantity()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    void missingInventoryRowFailsClosed() {
        when(inventoryRepository.findAllByMenuItemIdInForUpdate(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.reserve(request(UUID.randomUUID(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");
    }

    private InventoryReservationCommand request(UUID reservationId, int quantity) {
        return new InventoryReservationCommand(reservationId, 101L, 21L, 2021L, 7L,
                List.of(new InventoryReservationLineCommand(11L, quantity)));
    }

    private MenuItemInventoryReservation reservationRepositoryArgument() {
        var captor = org.mockito.ArgumentCaptor.forClass(MenuItemInventoryReservation.class);
        verify(reservationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
