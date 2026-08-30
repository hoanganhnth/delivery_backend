package com.delivery.simulator.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Test-only, run-scoped transport fault seam. It never reaches Gateway and is
 * deliberately limited to a single read poll so it cannot duplicate a domain
 * mutation.
 */
@Component
class GatewayFaultInjection {
    private final Set<String> armedPollFailures = ConcurrentHashMap.newKeySet();

    void armOneTransientPollFailure(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) armedPollFailures.add(correlationId);
    }

    boolean consumeTransientPollFailure(String correlationId, String method, String path) {
        return "GET".equals(method)
                && path != null && path.startsWith("/api/orders/")
                && correlationId != null
                && armedPollFailures.remove(correlationId);
    }
}
