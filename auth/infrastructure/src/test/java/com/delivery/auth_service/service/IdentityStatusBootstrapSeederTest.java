package com.delivery.auth_service.service;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.IdentityStatusBootstrapRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdentityStatusBootstrapSeederTest {
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final IdentityStatusBootstrapRepository receipts = mock(IdentityStatusBootstrapRepository.class);
    private final IdentityStatusOutboxService outbox = mock(IdentityStatusOutboxService.class);

    @Test
    void requiresBothFeatureFlagsAndExposesPendingGauge() {
        for (boolean[] flags : new boolean[][] {{false, false}, {false, true}, {true, false}}) {
            var metrics = new SimpleMeterRegistry();
            try {
                when(accounts.countWithoutIdentityStatusBootstrap()).thenReturn(3L);
                new IdentityStatusBootstrapSeeder(accounts, receipts, outbox, flags[0], flags[1], 100, metrics).seed();
                assertThat(metrics.get("delivery.identity.bootstrap.pending").tag("owner", "auth").gauge().value())
                        .isEqualTo(3.0);
            } finally {
                metrics.close();
            }
        }
        verify(accounts, never()).findWithoutIdentityStatusBootstrap(any());
        verifyNoInteractions(receipts, outbox);
    }

    @Test
    void clampsBatchSizeAndClaimsBeforeEmittingIncludingDuplicateReplay() {
        for (int batchSize : new int[] {0, 100, 1000}) {
            int expectedSize = Math.max(1, Math.min(batchSize, 500));
            AuthAccount account = new AuthAccount();
            org.springframework.test.util.ReflectionTestUtils.setField(account, "id", 7L);
            when(accounts.findWithoutIdentityStatusBootstrap(PageRequest.of(0, expectedSize))).thenReturn(List.of(account));
            var metrics = new SimpleMeterRegistry();
            try {
                var seeder = new IdentityStatusBootstrapSeeder(accounts, receipts, outbox, true, true, batchSize, metrics);
                for (Long version : new Long[] {null, 0L, 5L}) {
                    account.setLifecycleVersion(version);
                    long expectedVersion = version == null ? 1L : Math.max(1L, version);
                    when(receipts.claim(eq(7L), eq(expectedVersion), any())).thenReturn(1, 0);
                    clearInvocations(outbox);
                    seeder.seed();
                    seeder.seed();
                    verify(outbox).statusChanged(account, null, "BOOTSTRAP");
                    verifyNoMoreInteractions(outbox);
                }
            } finally {
                metrics.close();
            }
        }
    }
}
