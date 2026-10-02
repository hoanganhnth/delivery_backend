package com.delivery.restaurant_service;

import com.delivery.restaurant.application.DefaultOrderValidationUseCase;
import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.JpaOrderValidationCatalogAdapter;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true", "spring.kafka.listener.auto-startup=false",
        "app.outbox.relay-enabled=false", "app.search-sync.enabled=false", "order.service.url=http://order-service",
        "app.restaurant.inventory-enabled=false", "app.restaurant.serviceability-enabled=false"
})
class RestaurantOrderValidationPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired OrderValidationUseCase validation;
    @Autowired OrderCacheValidationService http;
    @Autowired RestaurantTransactionPort transactions;
    @Autowired RestaurantRepository restaurants;
    @Autowired MenuItemRepository menus;
    @Autowired JdbcTemplate sql;
    @MockitoSpyBean JpaOrderValidationCatalogAdapter catalog;

    @Test void productionCoreReadsCanonicalPricesAndHttpAdapterPreservesEveryResponseField() {
        var f = fixture();
        assertThat(validation).isInstanceOf(DefaultOrderValidationUseCase.class);
        var request = OrderValidationRequest.builder().restaurantId(f.restaurant()).items(List.of(
                OrderValidationRequest.OrderItemRequest.builder().menuItemId(f.menu()).quantity(2)
                        .menuItemName("Forged client name").price(0.01).build())).build();
        var result = http.validateOrderFromOrderService(request);
        assertThat(result.getIsValid()).isTrue(); assertThat(result.getCalculatedTotal()).isEqualTo(85.0);
        assertThat(result.getMessage()).isEqualTo("Order validation successful"); assertThat(result.getErrors()).isEmpty();
        var restaurant = result.getRestaurantInfo();
        assertThat(restaurant.getRestaurantId()).isEqualTo(f.restaurant()); assertThat(restaurant.getOwnerPrincipalId()).isEqualTo(100L);
        assertThat(restaurant.getCreatorId()).isEqualTo(200L); assertThat(restaurant.getRestaurantName()).startsWith("validation-");
        assertThat(restaurant.getRestaurantAddress()).isEqualTo("Canonical address"); assertThat(restaurant.getRestaurantPhone()).isEqualTo("0123456789");
        assertThat(restaurant.getLatitude()).isEqualTo(10.0); assertThat(restaurant.getLongitude()).isEqualTo(106.0);
        assertThat(restaurant.getDefaultPrepTimeMinutes()).isEqualTo(30); assertThat(restaurant.getIsAvailable()).isTrue(); assertThat(restaurant.getIsOpen()).isTrue();
        assertThat(restaurant.getOperatingHours()).isNull(); assertThat(restaurant.getServiceabilityEnabled()).isFalse();
        assertThat(restaurant.getServiceable()).isNull(); assertThat(restaurant.getServiceabilityZoneId()).isNull(); assertThat(restaurant.getServiceabilityZoneRevision()).isNull();
        assertThat(restaurant.getServiceabilityReason()).isEqualTo("CAPABILITY_DISABLED");
        var item = result.getItemValidations().get(0);
        assertThat(item.getMenuItemId()).isEqualTo(f.menu()); assertThat(item.getMenuItemName()).isEqualTo("Canonical meal");
        assertThat(item.getIsAvailable()).isTrue(); assertThat(item.getActualPrice()).isEqualTo(42.5); assertThat(item.getExpectedPrice()).isNull();
        assertThat(item.getPriceMatches()).isTrue(); assertThat(item.getRequestedQuantity()).isEqualTo(2);
        assertThat(item.getAvailableStock()).isNull(); assertThat(item.getHasEnoughStock()).isTrue();
        assertThat(http.validateOrderFromOrderService(null).getErrors().get(0).getErrorCode()).isEqualTo("RESTAURANT_ID_REQUIRED");
    }

    @Test void repeatableReadKeepsRestaurantAndMenuInSameSnapshotDuringConcurrentCommit() throws Exception {
        var f = fixture(); AtomicBoolean once = new AtomicBoolean(); var writer = Executors.newSingleThreadExecutor();
        doAnswer(invocation -> {
            var snapshot = invocation.callRealMethod();
            assertThat(sql.queryForObject("show transaction_isolation", String.class)).isEqualTo("repeatable read");
            assertThat(sql.queryForObject("show transaction_read_only", String.class)).isEqualTo("on");
            if (once.compareAndSet(false, true)) writer.submit(() -> transactions.required(() -> {
                sql.update("update restaurant set lifecycle_status = 'PAUSED', version = version + 1 where id = ?", f.restaurant());
                sql.update("update menu_item set price = 99, version = version + 1 where id = ?", f.menu());
                return null;
            })).get(20, TimeUnit.SECONDS);
            return snapshot;
        }).when(catalog).findRestaurant(anyLong());
        try {
            var first = validation.validate(f.command());
            assertThat(first.isValid()).isTrue(); assertThat(first.calculatedTotal()).isEqualTo(85.0);
            var next = validation.validate(f.command());
            assertThat(next.isValid()).isFalse(); assertThat(next.calculatedTotal()).isEqualTo(198.0);
            assertThat(next.errors()).extracting(OrderValidationError::errorCode).containsExactly("RESTAURANT_NOT_ACCEPTING_ORDERS");
            assertThat(next.errors().get(0).invalidValue()).isEqualTo(RestaurantStatus.PAUSED);
        } finally { writer.shutdownNow(); reset(catalog); }
    }

    @Test void archivedRestaurantAndUnavailableItemFailClosedWithCurrentCanonicalData() {
        var f = fixture();
        transactions.required(() -> {
            sql.update("update restaurant set lifecycle_status = 'ARCHIVED' where id = ?", f.restaurant());
            sql.update("update menu_item set status = 'SOLD_OUT' where id = ?", f.menu()); return null;
        });
        var result = validation.validate(f.command());
        assertThat(result.isValid()).isFalse(); assertThat(result.calculatedTotal()).isEqualTo(85.0);
        assertThat(result.errors()).extracting(OrderValidationError::errorCode)
                .containsExactly("RESTAURANT_NOT_ACCEPTING_ORDERS", "MENU_ITEM_NOT_AVAILABLE");
    }
    private Fixture fixture() {
        Restaurant row = new Restaurant(); row.setName("validation-" + UUID.randomUUID()); row.setCreatorId(200L); row.setOwnerPrincipalId(100L);
        row.setAddress("Canonical address"); row.setPhone("0123456789"); row.setAddressLat(10.0); row.setAddressLng(106.0);
        row = restaurants.saveAndFlush(row);
        MenuItem menu = new MenuItem(); menu.setName("Canonical meal"); menu.setPrice(new BigDecimal("42.50")); menu.setRestaurant(row); menu.setStatus(MenuItem.Status.AVAILABLE);
        return new Fixture(row.getId(), menus.saveAndFlush(menu).getId());
    }
    private record Fixture(Long restaurant, Long menu) {
        OrderValidationCommand command() { return new OrderValidationCommand(restaurant, null, null, List.of(new OrderValidationLineCommand(menu, 2))); }
    }
}
