package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import java.util.*;
import java.util.stream.Collectors;

public final class DefaultRegistrationAdmissionUseCase implements RegistrationAdmissionUseCase {
    private final boolean enabled;
    private final int percentage;
    private final Set<String> allowlist;
    private final RegistrationCohortPort cohort;
    private final RegistrationAdmissionTelemetryPort telemetry;
    public DefaultRegistrationAdmissionUseCase(boolean enabled, int percentage, String rawAllowlist,
            boolean hashKeyConfigured, RegistrationCohortPort cohort, RegistrationAdmissionTelemetryPort telemetry) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("app.identity.registration.canary-percentage must be between 0 and 100");
        }
        if (percentage > 0 && percentage < 100 && !hashKeyConfigured) {
            throw new IllegalArgumentException("A registration canary hash key is required when percentage is between 1 and 99");
        }
        this.enabled = enabled; this.percentage = percentage;
        this.allowlist = rawAllowlist == null || rawAllowlist.isBlank() ? Set.of()
                : Arrays.stream(rawAllowlist.split(",")).map(DefaultRegistrationAdmissionUseCase::normalize)
                        .filter(email -> !email.isBlank()).collect(Collectors.toUnmodifiableSet());
        this.cohort = Objects.requireNonNull(cohort); this.telemetry = Objects.requireNonNull(telemetry);
    }
    @Override public boolean admits(String email) {
        Decision decision = decide(email);
        telemetry.record(decision.admitted(), decision.mechanism());
        return decision.admitted();
    }
    private Decision decide(String email) {
        if (!enabled) return new Decision(false, "master_disabled");
        String canonical = normalize(email);
        if (allowlist.contains(canonical)) return new Decision(true, "allowlist");
        if (percentage == 100 || (percentage > 0 && cohort.bucket(canonical) < percentage)) {
            return new Decision(true, "percentage");
        }
        return new Decision(false, "cohort_closed");
    }
    private static String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
    private record Decision(boolean admitted, String mechanism) {}
}
