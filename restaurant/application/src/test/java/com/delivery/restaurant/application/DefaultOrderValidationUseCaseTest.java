package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultOrderValidationUseCaseTest {
    @Test void canonicalPriceAndNameAreUsedWithOneBulkReadAndRepeatableReadTransaction() {
        var f = new Fixture(); var result = f.core().validate(new OrderValidationCommand(1L, 10.75, 106.65,
                List.of(new OrderValidationLineCommand(2L, 1), new OrderValidationLineCommand(2L, 2))));
        assertTrue(result.isValid()); assertEquals(127.5, result.calculatedTotal());
        assertEquals("Canonical", result.itemValidations().get(0).menuItemName());
        assertNull(result.itemValidations().get(0).expectedPrice());
        assertEquals(List.of(2L), f.loadedIds); assertEquals(1, f.repeatableReads);
        assertEquals(100L, result.restaurantInfo().ownerPrincipalId());
        assertNull(result.restaurantInfo().serviceable());
    }
    @Test void requiredAndMissingRestaurantHaveStableFailureBodiesWithoutMenuReads() {
        var f = new Fixture();
        for (OrderValidationCommand invalid : Arrays.asList(null, new OrderValidationCommand(null, null, null, null))) {
            var result = f.core().validate(invalid); assertFalse(result.isValid()); assertNull(result.restaurantInfo());
            assertEquals("Restaurant is required", result.errors().get(0).message());
        }
        f.restaurant = null;
        var missing = f.core().validate(command(1)); assertFalse(missing.isValid());
        assertEquals("Restaurant does not exist", missing.errors().get(0).message());
        assertFalse(missing.restaurantInfo().isOpen()); assertFalse(missing.restaurantInfo().isAvailable());
        assertNull(f.loadedIds);
    }
    @Test void pausedArchivedClosedAndInvalidSchedulesFailClosed() {
        var f = new Fixture();
        for (RestaurantStatus status : List.of(RestaurantStatus.PAUSED, RestaurantStatus.ARCHIVED)) {
            f.restaurant = restaurant(status, null, null, "UTC");
            assertEquals("RESTAURANT_NOT_ACCEPTING_ORDERS", f.core().validate(command(1)).errors().get(0).errorCode());
        }
        for (RestaurantSnapshot closed : List.of(restaurant(RestaurantStatus.ACTIVE, LocalTime.of(1,0), LocalTime.of(2,0), "UTC"),
                restaurant(RestaurantStatus.ACTIVE, LocalTime.of(1,0), null, "UTC"),
                restaurant(RestaurantStatus.ACTIVE, null, null, "invalid-zone"))) {
            f.restaurant = closed; var result = f.core().validate(command(1)); assertFalse(result.isValid());
            assertEquals("RESTAURANT_CLOSED", result.errors().get(0).errorCode());
        }
        f.restaurant = restaurant(RestaurantStatus.ACTIVE, LocalTime.of(8,0), LocalTime.of(20,0), "UTC");
        assertTrue(f.core().validate(command(1)).isValid());
        assertEquals("08:00 - 20:00", f.core().validate(command(1)).restaurantInfo().operatingHours());
    }
    @Test void enabledServiceabilityCarriesZoneIdentityOrFailsClosedWithCoordinateError() {
        var f = new Fixture(); f.decision = new ServiceabilityDecision(true, true, 88L, 3L, "MATCHED_ZONE");
        var matched = f.core().validate(command(1)); assertTrue(matched.isValid());
        assertTrue(matched.restaurantInfo().serviceabilityEnabled()); assertTrue(matched.restaurantInfo().serviceable());
        assertEquals(88L, matched.restaurantInfo().serviceabilityZoneId()); assertEquals(3L, matched.restaurantInfo().serviceabilityZoneRevision());
        f.decision = new ServiceabilityDecision(true, false, null, null, "OUTSIDE_ACTIVE_ZONES");
        var rejected = f.core().validate(command(1)); assertFalse(rejected.isValid());
        assertEquals("OUTSIDE_ACTIVE_ZONES", rejected.errors().get(0).errorCode()); assertEquals("10.75,106.65", rejected.errors().get(0).invalidValue());
    }
    @Test void stockSignalIsAdvisoryAndOnlyQueriedForAvailableCanonicalItems() {
        var f = new Fixture(); f.inventoryEnabled = true;
        f.stock = new InventoryAvailability(false, 0);
        var rejected = f.core().validate(command(1)); assertFalse(rejected.isValid());
        assertTrue(rejected.itemValidations().get(0).isAvailable()); assertFalse(rejected.itemValidations().get(0).hasEnoughStock());
        assertEquals("INSUFFICIENT_STOCK", rejected.errors().get(0).errorCode()); assertEquals(1, f.stockReads);
        f.stock = new InventoryAvailability(true, 5); assertTrue(f.core().validate(command(1)).isValid());
        int reads = f.stockReads;
        for (Integer quantity : Arrays.asList(null, 0, -1)) { assertFalse(f.core().validate(command(quantity)).isValid()); }
        assertEquals(reads, f.stockReads);
    }
    @Test void missingForeignInactiveAndMalformedCanonicalItemsFailClosed() {
        var f = new Fixture(); f.inventoryEnabled = true;
        List<MenuItemSnapshot> invalids = Arrays.asList(item(null,"Canonical",BigDecimal.TEN,MenuItemStatus.AVAILABLE),
                item(99L,"Canonical",BigDecimal.TEN,MenuItemStatus.AVAILABLE), item(1L,null,BigDecimal.TEN,MenuItemStatus.AVAILABLE),
                item(1L," ",BigDecimal.TEN,MenuItemStatus.AVAILABLE), item(1L,"Canonical",null,MenuItemStatus.AVAILABLE),
                item(1L,"Canonical",BigDecimal.ZERO,MenuItemStatus.AVAILABLE), item(1L,"Canonical",BigDecimal.TEN,MenuItemStatus.SOLD_OUT));
        for (var invalid : invalids) { f.items = List.of(invalid); assertFalse(f.core().validate(command(1)).isValid()); }
        f.items = List.of(); assertFalse(f.core().validate(command(1)).isValid());
        assertEquals(0, f.stockReads);
    }
    @Test void nullCartLinesAndNullIdentitiesFailClosedWhileAbsentCartKeepsExistingContract() {
        var f = new Fixture();
        var invalid = f.core().validate(new OrderValidationCommand(1L, null, null,
                Arrays.asList(null, new OrderValidationLineCommand(null, null))));
        assertFalse(invalid.isValid()); assertEquals(4, invalid.errors().size()); assertEquals(0.0, invalid.calculatedTotal());
        assertNull(f.loadedIds);
        assertTrue(f.core().validate(new OrderValidationCommand(1L,null,null,null)).isValid());
        assertTrue(f.core().validate(new OrderValidationCommand(1L,null,null,List.of())).isValid());
    }
    private static OrderValidationCommand command(Integer quantity) { return new OrderValidationCommand(1L,10.75,106.65,List.of(new OrderValidationLineCommand(2L,quantity))); }
    private static RestaurantSnapshot restaurant(RestaurantStatus status, LocalTime open, LocalTime close, String zone) {
        return new RestaurantSnapshot(1L,"Restaurant","Address","Phone",open,close,20,null,null,10.0,106.0,null,null,status,0L,zone,100L,200L);
    }
    private static MenuItemSnapshot item(Long restaurant, String name, BigDecimal price, MenuItemStatus status) { return new MenuItemSnapshot(2L,restaurant,name,null,price,status,null,null,null,0L); }
    private static class Fixture implements OrderValidationCatalogPort, RestaurantTransactionPort, RestaurantServiceabilityUseCase, MenuItemInventoryUseCase {
        RestaurantSnapshot restaurant = restaurant(RestaurantStatus.ACTIVE,null,null,"UTC");
        List<MenuItemSnapshot> items = List.of(item(1L,"Canonical",new BigDecimal("42.50"),MenuItemStatus.AVAILABLE));
        List<Long> loadedIds; int repeatableReads, stockReads; boolean inventoryEnabled;
        ServiceabilityDecision decision = new ServiceabilityDecision(false,false,null,null,"CAPABILITY_DISABLED");
        InventoryAvailability stock = new InventoryAvailability(true,5);
        DefaultOrderValidationUseCase core() { return new DefaultOrderValidationUseCase(this,this,()->inventoryEnabled?this:null,
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"),ZoneOffset.UTC),this); }
        public <T> T repeatableRead(Supplier<T> operation) { repeatableReads++; return operation.get(); }
        public <T> T required(Supplier<T> operation) { throw new UnsupportedOperationException(); }
        public <T> T readOnly(Supplier<T> operation) { throw new UnsupportedOperationException(); }
        public Optional<RestaurantSnapshot> findRestaurant(Long id) { return Optional.ofNullable(restaurant); }
        public List<MenuItemSnapshot> findItems(List<Long> ids) { loadedIds = ids; return items; }
        public ServiceabilityDecision evaluate(Long id,Double latitude,Double longitude) { return decision; }
        public InventoryAvailability availability(Long restaurant,Long item,Integer quantity) { stockReads++; return stock; }
        public List<ServiceabilityZoneResult> list(Long id,Long principal,Long legacy,RestaurantActorRole role) { throw new UnsupportedOperationException(); }
        public ServiceabilityZoneResult create(Long id,CreateServiceabilityZoneCommand command,Long principal,Long legacy,RestaurantActorRole role) { throw new UnsupportedOperationException(); }
        public ServiceabilityZoneResult update(Long id,Long zone,UpdateServiceabilityZoneCommand command,Long principal,Long legacy,RestaurantActorRole role) { throw new UnsupportedOperationException(); }
        public void delete(Long id,Long zone,Long principal,Long legacy,RestaurantActorRole role) { throw new UnsupportedOperationException(); }
        public InventoryReservationResult reserve(InventoryReservationCommand command) { throw new UnsupportedOperationException(); }
        public InventoryReservationResult commit(UUID id,Long order) { throw new UnsupportedOperationException(); }
        public InventoryReservationResult release(UUID id,Long order) { throw new UnsupportedOperationException(); }
        public int expireReservations() { throw new UnsupportedOperationException(); }
        public MenuItemInventoryResult getInventory(Long id) { throw new UnsupportedOperationException(); }
        public MenuItemInventoryResult updateInventory(Long id,UpdateMenuItemInventoryCommand command) { throw new UnsupportedOperationException(); }
    }
}
