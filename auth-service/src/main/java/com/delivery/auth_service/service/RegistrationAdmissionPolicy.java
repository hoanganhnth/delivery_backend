package com.delivery.auth_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Admission control for the public password-registration rollout.
 *
 * <p>The master switch remains fail-closed. Once it is open, operators can
 * admit known canary accounts or a stable, keyed percentage cohort without
 * emitting email addresses, hashes, or buckets into logs/metrics. A partial
 * percentage deliberately requires a non-public HMAC key so a caller cannot
 * choose email addresses until it lands in the enabled bucket.</p>
 */
@Service
public class RegistrationAdmissionPolicy {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final boolean publicRegistrationEnabled;
    private final int canaryPercentage;
    private final Set<String> allowlist;
    private final byte[] cohortKey;
    private final Counter masterDisabled;
    private final Counter allowlistAdmitted;
    private final Counter percentageAdmitted;
    private final Counter cohortClosed;

    public RegistrationAdmissionPolicy(
            @Value("${app.identity.public-registration-enabled:false}") boolean publicRegistrationEnabled,
            @Value("${app.identity.registration.canary-percentage:0}") int canaryPercentage,
            @Value("${app.identity.registration.canary-allowlist:}") String canaryAllowlist,
            @Value("${app.identity.registration.canary-hash-key:}") String canaryHashKey,
            MeterRegistry metrics) {
        if (canaryPercentage < 0 || canaryPercentage > 100) {
            throw new IllegalArgumentException("app.identity.registration.canary-percentage must be between 0 and 100");
        }
        if (canaryPercentage > 0 && canaryPercentage < 100 && (canaryHashKey == null || canaryHashKey.isBlank())) {
            throw new IllegalArgumentException(
                    "A registration canary hash key is required when percentage is between 1 and 99");
        }
        this.publicRegistrationEnabled = publicRegistrationEnabled;
        this.canaryPercentage = canaryPercentage;
        this.allowlist = parseAllowlist(canaryAllowlist);
        this.cohortKey = canaryHashKey == null ? new byte[0] : canaryHashKey.getBytes(StandardCharsets.UTF_8);
        this.masterDisabled = counter(metrics, "rejected", "master_disabled");
        this.allowlistAdmitted = counter(metrics, "admitted", "allowlist");
        this.percentageAdmitted = counter(metrics, "admitted", "percentage");
        this.cohortClosed = counter(metrics, "rejected", "cohort_closed");
    }

    public boolean admits(String email) {
        if (!publicRegistrationEnabled) {
            masterDisabled.increment();
            return false;
        }
        String normalizedEmail = normalize(email);
        if (allowlist.contains(normalizedEmail)) {
            allowlistAdmitted.increment();
            return true;
        }
        if (canaryPercentage == 100) {
            percentageAdmitted.increment();
            return true;
        }
        if (canaryPercentage > 0 && bucket(normalizedEmail) < canaryPercentage) {
            percentageAdmitted.increment();
            return true;
        }
        cohortClosed.increment();
        return false;
    }

    private static Counter counter(MeterRegistry metrics, String outcome, String mechanism) {
        return Counter.builder("delivery.identity.registration.admission")
                .tag("outcome", outcome)
                .tag("mechanism", mechanism)
                .register(metrics);
    }

    private static Set<String> parseAllowlist(String rawAllowlist) {
        if (rawAllowlist == null || rawAllowlist.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawAllowlist.split(","))
                .map(RegistrationAdmissionPolicy::normalize)
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private int bucket(String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(cohortKey, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            long value = ((long) (digest[0] & 0xff) << 24)
                    | ((long) (digest[1] & 0xff) << 16)
                    | ((long) (digest[2] & 0xff) << 8)
                    | (digest[3] & 0xffL);
            return (int) (value % 100);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate registration cohort", exception);
        }
    }
}
