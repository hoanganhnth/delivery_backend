package com.delivery.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicPolylineTest {
    @Test
    void sameSeedProducesTheSameRouteAndSpeedControlsProgress() {
        var first = new DeterministicPolyline(10.77, 106.69, 10.78, 106.70, 42L);
        var second = new DeterministicPolyline(10.77, 106.69, 10.78, 106.70, 42L);
        assertThat(first.positionAfterSeconds(10, 30)).isEqualTo(second.positionAfterSeconds(10, 30));
        assertThat(first.positionAfterSeconds(3600, 30).latitude()).isEqualTo(10.78);
    }
}
