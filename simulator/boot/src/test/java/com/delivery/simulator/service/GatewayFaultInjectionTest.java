package com.delivery.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayFaultInjectionTest {

    @Test
    void consumesAnArmedTransientFaultOnlyOnceForTheSameRunAndOperation() {
        GatewayFaultInjection faults = new GatewayFaultInjection();
        faults.armOneTransientPollFailure("run-1");

        assertThat(faults.consumeTransientPollFailure("run-1", "GET", "/api/orders/42")).isTrue();
        assertThat(faults.consumeTransientPollFailure("run-1", "GET", "/api/orders/42")).isFalse();
        assertThat(faults.consumeTransientPollFailure("run-1", "POST", "/api/orders")).isFalse();
    }
}
