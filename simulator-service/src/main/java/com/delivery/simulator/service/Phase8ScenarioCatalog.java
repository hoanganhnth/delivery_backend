package com.delivery.simulator.service;

import java.util.List;

/** Deterministic, production-isolated scenarios used by the Phase 8 regression sweep. */
public final class Phase8ScenarioCatalog {
    private Phase8ScenarioCatalog() {}

    public record Scenario(String name, long seed, boolean isolatedFromProduction) {}

    public static List<Scenario> defaults() {
        return List.of(
                new Scenario("duplicate-kafka-delivery", 8001L, true),
                new Scenario("consumer-restart", 8002L, true),
                new Scenario("search-replay", 8003L, true),
                new Scenario("voucher-contention", 8004L, true),
                new Scenario("flash-sale-stock-contention", 8005L, true),
                new Scenario("livestream-checkout-retry", 8006L, true),
                new Scenario("soft-delete-recovery", 8007L, true));
    }
}
