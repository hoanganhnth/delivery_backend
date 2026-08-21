package com.delivery.auth_service.service;

import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.IdentityStatusBootstrapRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emits one authoritative lifecycle snapshot for each existing linked account
 * before the Auth status relay is enabled. The bootstrap receipt is local Auth
 * data and prevents duplicate snapshots across Pod restarts or replicas.
 */
@Service
public class IdentityStatusBootstrapSeeder {
    private final AuthAccountRepository accounts;
    private final IdentityStatusBootstrapRepository bootstraps;
    private final IdentityStatusOutboxService outbox;
    private final boolean enabled;
    private final boolean identityEventsEnabled;
    private final int batchSize;

    public IdentityStatusBootstrapSeeder(AuthAccountRepository accounts,
            IdentityStatusBootstrapRepository bootstraps,
            IdentityStatusOutboxService outbox,
            @Value("${app.identity.status-bootstrap.enabled:false}") boolean enabled,
            @Value("${app.identity.events.enabled:false}") boolean identityEventsEnabled,
            @Value("${app.identity.status-bootstrap.batch-size:100}") int batchSize,
            MeterRegistry metrics) {
        this.accounts = accounts;
        this.bootstraps = bootstraps;
        this.outbox = outbox;
        this.enabled = enabled;
        this.identityEventsEnabled = identityEventsEnabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        Gauge.builder("delivery.identity.bootstrap.pending", accounts,
                AuthAccountRepository::countWithoutIdentityStatusBootstrap)
                .tag("owner", "auth").tag("event", "status_changed").register(metrics);
    }

    @Scheduled(fixedDelayString = "${app.identity.status-bootstrap.poll-delay-ms:1000}")
    @Transactional
    void seed() {
        if (!enabled || !identityEventsEnabled) return;
        for (var account : accounts.findWithoutIdentityStatusBootstrap(PageRequest.of(0, batchSize))) {
            long version = account.getLifecycleVersion() == null ? 1L : Math.max(1L, account.getLifecycleVersion());
            if (bootstraps.claim(account.getId(), version, LocalDateTime.now()) == 1) {
                outbox.statusChanged(account, null, "BOOTSTRAP");
            }
        }
    }
}
