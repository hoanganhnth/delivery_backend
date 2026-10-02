package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.serviceability.*;
import com.delivery.restaurant.domain.serviceability.ServiceabilityGeometry.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultRestaurantServiceabilityUseCaseTest {
    private static final RestaurantActorRole OWNER = RestaurantActorRole.SHOP_OWNER;
    private static final RestaurantActorRole ADMIN = RestaurantActorRole.ADMIN;
    private static final Polygon SQUARE = new Polygon(List.of(new Point(106, 10), new Point(107, 10),
            new Point(107, 11), new Point(106, 11), new Point(106, 10)));

    @Test void disabledAndInvalidCoordinatesNeverReadPersistence() {
        var f = new Fixture();
        assertEquals("CAPABILITY_DISABLED", f.core(false, false).evaluate(7L, 10.5, 106.5).reason());
        var core = f.core(true, false);
        for (Double[] point : List.of(new Double[]{null, 106.5}, new Double[]{10.5, null},
                new Double[]{Double.NaN, 106.5}, new Double[]{10.5, Double.NaN}, new Double[]{25.0, 106.5})) {
            assertEquals("INVALID_DELIVERY_COORDINATE", core.evaluate(7L, point[0], point[1]).reason());
        }
        assertEquals(0, f.reads);
        assertTrue(f.calls.stream().allMatch("readOnly"::equals));
    }

    @Test void coverageKeepsOrderedPriorityRevisionAndFailClosedReasons() {
        var f = new Fixture(); var core = f.core(true, false);
        f.exists = false;
        assertEquals("RESTAURANT_NOT_FOUND", core.evaluate(7L, 10.5, 106.5).reason());
        f.exists = true;
        assertEquals("NO_ACTIVE_ZONE", core.evaluate(7L, 10.5, 106.5).reason());
        f.zones = List.of(zone(1L, 7L, false, "invalid"));
        assertEquals("OUTSIDE_ACTIVE_ZONES", core.evaluate(7L, 10.5, 106.5).reason());
        f.zones = List.of(zone(2L, 7L, true, "square"), zone(3L, 7L, true, "invalid"));
        var match = core.evaluate(7L, 10.5, 106.5);
        assertTrue(match.serviceable()); assertEquals(2L, match.zoneId()); assertEquals(3L, match.zoneRevision());
        f.zones = List.of(zone(2L, 7L, true, "square"));
        assertEquals("OUTSIDE_ACTIVE_ZONES", core.evaluate(7L, 12.0, 106.5).reason());
        f.zones = List.of(zone(4L, 7L, true, "invalid"), zone(2L, 7L, true, "square"));
        assertEquals("INVALID_ZONE_CONFIGURATION", core.evaluate(7L, 10.5, 106.5).reason());
        assertEquals(List.of(4L), f.incidents);
    }

    @Test void ownershipSupportsPrincipalAndAuthorizedLegacyFallback() {
        var f = new Fixture(); var core = f.core(true, false);
        assertEquals(f.zones, core.list(7L, 10L, 20L, OWNER));
        f.owner = new ServiceabilityStorePort.RestaurantOwner(null, 20L);
        assertEquals(f.zones, core.list(7L, 10L, 20L, OWNER));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> f.core(true, true).list(7L, 10L, 20L, OWNER));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.list(7L, 10L, 21L, OWNER));
        f.owner = new ServiceabilityStorePort.RestaurantOwner(11L, 20L);
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.list(7L, 10L, 20L, OWNER));
        f.owner = null;
        assertThrows(ServiceabilityResourceNotFoundException.class, () -> core.list(7L, 10L, 20L, OWNER));
        assertEquals(f.zones, core.list(7L, 10L, 20L, ADMIN));
    }

    @Test void rejectsMissingIdentityAndUnauthorizedRoleBeforeDataAccess() {
        var f = new Fixture(); var core = f.core(true, false);
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.list(null, 10L, 20L, ADMIN));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.list(7L, null, 20L, ADMIN));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.list(7L, 10L, null, ADMIN));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.list(7L, 10L, 20L, null));
        assertEquals(0, f.reads);
    }

    @Test void creationAppliesDefaultsOnlyAfterOwnershipAndGeometryValidation() {
        var f = new Fixture(); var core = f.core(true, false);
        var saved = core.create(7L, new CreateServiceabilityZoneCommand(" Zone ", " square ", null, null), 10L, 20L, OWNER);
        assertEquals("Zone", saved.name()); assertEquals("square", saved.polygonGeoJson());
        assertEquals(0, saved.priority()); assertTrue(saved.active());
        assertEquals(List.of("required", "owner", "parse", "save"), f.calls);
        saved = core.create(7L, new CreateServiceabilityZoneCommand("Zone", "square", 4, false), 10L, 20L, ADMIN);
        assertEquals(4, saved.priority()); assertFalse(saved.active());
        assertThrows(IllegalArgumentException.class, () -> core.create(7L, null, 10L, 20L, ADMIN));
        int writes = f.writes;
        assertThrows(IllegalArgumentException.class, () -> core.create(7L,
                new CreateServiceabilityZoneCommand("Zone", "invalid", 0, true), 10L, 20L, ADMIN));
        assertEquals(writes, f.writes);
    }

    @Test void updateKeepsUnspecifiedFieldsAndRequiresMatchingRevision() {
        var f = new Fixture(); var core = f.core(true, false); f.zones = List.of(zone(1L, 7L, true, "square"));
        var saved = core.update(7L, 1L, new UpdateServiceabilityZoneCommand(3L, null, null, null, null), 10L, 20L, ADMIN);
        assertEquals(f.zones.get(0), saved);
        saved = core.update(7L, 1L, new UpdateServiceabilityZoneCommand(3L, " New ", " square ", 8, false), 10L, 20L, OWNER);
        assertEquals("New", saved.name()); assertEquals("square", saved.polygonGeoJson());
        assertEquals(8, saved.priority()); assertFalse(saved.active());
        int writes = f.writes;
        for (Long revision : Arrays.asList(null, 2L)) {
            assertThrows(ServiceabilityZoneConflictException.class, () -> core.update(7L, 1L,
                    new UpdateServiceabilityZoneCommand(revision, null, null, null, null), 10L, 20L, ADMIN));
        }
        assertThrows(IllegalArgumentException.class, () -> core.update(7L, 1L,
                new UpdateServiceabilityZoneCommand(3L, " ", null, null, null), 10L, 20L, ADMIN));
        assertThrows(IllegalArgumentException.class, () -> core.update(7L, 1L,
                new UpdateServiceabilityZoneCommand(3L, null, "invalid", null, null), 10L, 20L, ADMIN));
        assertThrows(IllegalArgumentException.class, () -> core.update(7L, 1L, null, 10L, 20L, ADMIN));
        assertEquals(writes, f.writes);
    }

    @Test void updateAndDeleteRequireAnExistingZoneOwnedByRestaurant() {
        var f = new Fixture(); var core = f.core(true, false);
        assertThrows(ServiceabilityResourceNotFoundException.class, () -> core.delete(7L, 1L, 10L, 20L, ADMIN));
        f.zones = List.of(zone(1L, 8L, true, "square"));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.delete(7L, 1L, 10L, 20L, ADMIN));
        assertThrows(ServiceabilityAccessDeniedException.class, () -> core.update(7L, 1L,
                new UpdateServiceabilityZoneCommand(3L, null, null, null, null), 10L, 20L, ADMIN));
        assertEquals(0, f.writes);
        f.zones = List.of(zone(1L, 7L, true, "square"));
        core.delete(7L, 1L, 10L, 20L, OWNER);
        assertEquals(List.of(1L), f.deleted);
    }

    private static ServiceabilityZoneResult zone(Long id, Long restaurant, boolean active, String polygon) {
        return new ServiceabilityZoneResult(id, restaurant, "Zone", polygon, 1, active, 3L, null, null);
    }

    private static class Fixture implements ServiceabilityStorePort, ServiceabilityPolygonPort,
            ServiceabilityIncidentPort, RestaurantTransactionPort {
        RestaurantOwner owner = new RestaurantOwner(10L, 20L);
        boolean exists = true;
        List<ServiceabilityZoneResult> zones = List.of();
        List<String> calls = new ArrayList<>();
        List<Long> incidents = new ArrayList<>(), deleted = new ArrayList<>();
        int reads, writes;
        DefaultRestaurantServiceabilityUseCase core(boolean enabled, boolean enforced) {
            return new DefaultRestaurantServiceabilityUseCase(this, this, this, this, enabled, enforced);
        }
        public <T> T readOnly(Supplier<T> work) { calls.add("readOnly"); return work.get(); }
        public <T> T required(Supplier<T> work) { calls.add("required"); return work.get(); }
        public Optional<RestaurantOwner> findRestaurant(Long id) { reads++; calls.add("owner"); return Optional.ofNullable(owner); }
        public boolean restaurantExists(Long id) { reads++; return exists; }
        public List<ServiceabilityZoneResult> orderedZones(Long id) { reads++; return zones; }
        public Optional<ServiceabilityZoneResult> findZone(Long id) { reads++; return zones.stream().filter(z -> z.id().equals(id)).findFirst(); }
        public ServiceabilityZoneResult save(ServiceabilityZoneResult zone) { writes++; calls.add("save"); return zone; }
        public void delete(Long id) { writes++; deleted.add(id); }
        public Polygon parse(String json) { calls.add("parse"); if (!json.trim().equals("square")) throw new IllegalArgumentException("invalid polygon"); return SQUARE; }
        public void invalidZone(Long id, Long restaurantId) { incidents.add(id); }
    }
}
