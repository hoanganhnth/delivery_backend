package com.delivery.restaurant_service.controller;

import com.delivery.restaurant.application.DefaultLivestreamProductUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant_service.service.JpaLivestreamProductReadAdapter;
import java.util.function.Supplier;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class InternalLivestreamProductControllerTest {
    private final MenuItemRepository menu = mock(MenuItemRepository.class);
    private final InternalLivestreamProductController controller =
            new InternalLivestreamProductController(new DefaultLivestreamProductUseCase(
                    new JpaLivestreamProductReadAdapter(menu), new RestaurantTransactionPort() {
                        public <T> T readOnly(Supplier<T> work) { return work.get(); }
                        public <T> T required(Supplier<T> work) { throw new UnsupportedOperationException(); }
                        public <T> T repeatableRead(Supplier<T> work) { throw new UnsupportedOperationException(); }
                    }), "test-only-secret");

    @Test
    void deniesMissingOrWrongInternalTokenBeforeQuery() {
        assertThat(controller.get(42L, 10L, null).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.get(42L, 10L, "wrong").getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(menu);
    }
    @Test
    void returnsNotFoundForMissingForeignOrUnavailableProduct() {
        when(menu.findById(10L)).thenReturn(Optional.empty());
        assertThat(controller.get(42L, 10L, "test-only-secret").getStatusCode().value()).isEqualTo(404);
        var item = item(43L);
        when(menu.findById(10L)).thenReturn(Optional.of(item));
        assertThat(controller.get(42L, 10L, "test-only-secret").getStatusCode().value()).isEqualTo(404);
        item.setRestaurant(restaurant(42L));
        item.setStatus(null);
        assertThat(controller.get(42L, 10L, "test-only-secret").getStatusCode().value()).isEqualTo(404);
    }
    @Test
    void suppliesCanonicalMenuAndRestaurantMetadata() {
        when(menu.findById(10L)).thenReturn(Optional.of(item(42L)));
        var result = controller.get(42L, 10L, "test-only-secret");
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getData().productName()).isEqualTo("Món canonical");
        assertThat(result.getBody().getData().restaurantName()).isEqualTo("Quán canonical");
    }
    private Restaurant restaurant(long id) {
        var r = new Restaurant(); r.setId(id); r.setName("Quán canonical"); return r;
    }
    private MenuItem item(long restaurantId) {
        var item = new MenuItem(); item.setId(10L); item.setName("Món canonical");
        item.setRestaurant(restaurant(restaurantId)); item.setStatus(MenuItem.Status.AVAILABLE);
        return item;
    }
}
