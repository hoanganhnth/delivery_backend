package com.delivery.auth_service.service;

import com.delivery.auth.application.api.RegistrationAdmissionTelemetryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RegistrationAdmissionMetrics implements RegistrationAdmissionTelemetryPort {
    private final Map<String, Counter> counters;
    public RegistrationAdmissionMetrics(MeterRegistry registry) {
        counters = Map.of("false:master_disabled", counter(registry, "rejected", "master_disabled"),
                "true:allowlist", counter(registry, "admitted", "allowlist"),
                "true:percentage", counter(registry, "admitted", "percentage"),
                "false:cohort_closed", counter(registry, "rejected", "cohort_closed"));
    }
    @Override public void record(boolean admitted, String mechanism) {
        counters.get(admitted + ":" + mechanism).increment();
    }
    private static Counter counter(MeterRegistry registry, String outcome, String mechanism) {
        return Counter.builder("delivery.identity.registration.admission").tag("outcome", outcome)
                .tag("mechanism", mechanism).register(registry);
    }
}
