package com.delivery.user_service.service;

import com.delivery.identity.contracts.IdentityProfileCreated;
import com.delivery.user_service.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gradually makes legacy User profiles visible to Auth through the same
 * profile-created outbox contract as new registration. Every mapping comes
 * from User's own principal_id; it never guesses a principal from a domain ID.
 */
@Service
public class IdentityProfileOutboxSeeder {
    private final UserRepository users;
    private final IdentityOutboxService outbox;
    private final boolean relayEnabled;
    private final int batchSize;

    public IdentityProfileOutboxSeeder(UserRepository users, IdentityOutboxService outbox,
            @Value("${app.identity.outbox.relay-enabled:false}") boolean relayEnabled,
            @Value("${app.identity.outbox.seed-batch-size:100}") int batchSize,
            MeterRegistry metrics) {
        this.users = users;
        this.outbox = outbox;
        this.relayEnabled = relayEnabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        Gauge.builder("delivery.identity.bootstrap.pending", users,
                repository -> repository.countWithoutIdentityProfileEvent(IdentityProfileCreated.TYPE))
                .tag("owner", "user").tag("event", "profile_created").register(metrics);
    }

    @Scheduled(fixedDelayString = "${app.identity.outbox.seed-poll-delay-ms:1000}")
    @Transactional
    void seed() {
        if (!relayEnabled) return;
        List<com.delivery.user_service.entity.User> batch = users.findWithoutIdentityProfileEvent(
                IdentityProfileCreated.TYPE, PageRequest.of(0, batchSize));
        for (var user : batch) {
            outbox.profileCreated(user.getPrincipalId(), user.getId());
        }
    }
}
