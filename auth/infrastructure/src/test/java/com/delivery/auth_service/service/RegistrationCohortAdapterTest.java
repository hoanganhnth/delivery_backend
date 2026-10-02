package com.delivery.auth_service.service;

import com.delivery.auth.application.DefaultRegistrationAdmissionUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationCohortAdapterTest {
    @Test void preservesHmacCohortVectorsIncludingUnsignedHighBit() {
        var adapter = new HmacRegistrationCohortAdapter("fixture-secret");
        assertThat(adapter.bucket("user@example.com")).isEqualTo(16);
        assertThat(adapter.bucket("canary@example.com")).isEqualTo(59);
        assertThat(adapter.bucket("a@example.com")).isEqualTo(58);
        assertThat(adapter.bucket("user@example.com")).isEqualTo(16);
    }
    @Test void coreAndTechnicalAdaptersKeepBoundedAdmissionMetricsAndThreshold() {
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new RegistrationAdmissionMetrics(registry);
            var cohort = new HmacRegistrationCohortAdapter("fixture-secret");
            var partial = new DefaultRegistrationAdmissionUseCase(true, 59, "known@example.com", true, cohort, metrics);
            assertThat(partial.admits(" USER@example.com ")).isTrue();
            assertThat(partial.admits("canary@example.com")).isFalse();
            assertThat(partial.admits("known@example.com")).isTrue();
            assertThat(new DefaultRegistrationAdmissionUseCase(false, 100, "", false, cohort, metrics).admits("known@example.com")).isFalse();
            assertThat(registry.getMeters()).hasSize(4);
            for (var mechanism : new String[]{"allowlist", "percentage", "cohort_closed", "master_disabled"}) {
                assertThat(registry.get("delivery.identity.registration.admission").tag("mechanism", mechanism).counter().count()).isEqualTo(1.0);
            }
            assertThat(registry.getMeters()).allSatisfy(meter -> {
                assertThat(meter.getId().getTags()).hasSize(2);
                assertThat(meter.getId().getTag("email")).isNull();
            });
        } finally { registry.close(); }
    }
}
