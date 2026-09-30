package com.delivery.simulator.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Phase8ScenarioTest {
    @Test
    void catalogIsDeterministicAndCoversPhase8FailureModes() {
        var first = Phase8ScenarioCatalog.defaults();
        var second = Phase8ScenarioCatalog.defaults();

        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(Phase8ScenarioCatalog.Scenario::name)
                .containsExactlyInAnyOrder("duplicate-kafka-delivery", "consumer-restart", "search-replay",
                        "voucher-contention", "flash-sale-stock-contention", "livestream-checkout-retry",
                        "soft-delete-recovery");
        assertThat(first).allMatch(Phase8ScenarioCatalog.Scenario::isolatedFromProduction);
        assertThat(first).extracting(Phase8ScenarioCatalog.Scenario::seed).doesNotHaveDuplicates();
    }
}
