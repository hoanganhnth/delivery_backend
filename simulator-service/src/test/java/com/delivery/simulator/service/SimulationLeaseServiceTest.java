package com.delivery.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.delivery.simulator.entity.SimulationActorLease;
import com.delivery.simulator.repository.SimulationActorLeaseRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SimulationLeaseServiceTest {
    @Test
    void reclaimMarksExpiredActiveLeasesAsExpired() {
        var repository = Mockito.mock(SimulationActorLeaseRepository.class);
        var lease = new SimulationActorLease(UUID.randomUUID(), UUID.randomUUID(), 9L, 4L,
                Instant.now().minusSeconds(1), "ACTIVE");
        when(repository.findByStatusAndLeaseExpiresAtBefore(Mockito.eq("ACTIVE"), Mockito.any(Instant.class)))
                .thenReturn(List.of(lease));
        var service = new SimulationLeaseService(repository, 15, true);

        assertThat(service.reclaimExpired()).isEqualTo(1);
        assertThat(lease.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void releaseMarksLeaseQuarantinedWhenFenceDoesNotMatch() {
        var repository = Mockito.mock(SimulationActorLeaseRepository.class);
        UUID leaseId = UUID.randomUUID();
        var lease = new SimulationActorLease(leaseId, UUID.randomUUID(), 9L, 4L,
                Instant.now().plusSeconds(10), "ACTIVE");
        when(repository.findById(leaseId)).thenReturn(Optional.of(lease));
        var service = new SimulationLeaseService(repository, 15, true);

        assertThat(service.releaseOrQuarantine(leaseId, 3L)).isFalse();
        assertThat(lease.getStatus()).isEqualTo("QUARANTINED");
    }

    @Test
    void explicitQuarantinePreventsAnActiveLeaseFromBeingReused() {
        var repository = Mockito.mock(SimulationActorLeaseRepository.class);
        UUID leaseId = UUID.randomUUID();
        var lease = new SimulationActorLease(leaseId, UUID.randomUUID(), 9L, 4L,
                Instant.now().plusSeconds(10), "ACTIVE");
        when(repository.findById(leaseId)).thenReturn(Optional.of(lease));
        var service = new SimulationLeaseService(repository, 15, true);

        assertThat(service.quarantine(leaseId, 4L)).isTrue();
        assertThat(lease.getStatus()).isEqualTo("QUARANTINED");
    }
    @Test
    void claimCreatesALeaseWithAIncreasingFence() {
        var repository = Mockito.mock(SimulationActorLeaseRepository.class);
        when(repository.findTopByPrincipalIdOrderByFencingTokenDesc(9L)).thenReturn(Optional.empty());
        var service = new SimulationLeaseService(repository, 15, true);

        var lease = service.claim(UUID.randomUUID(), 9L);

        assertThat(lease.getFencingToken()).isEqualTo(1L);
        assertThat(lease.getStatus()).isEqualTo("ACTIVE");
    }
    @Test
    void renewRejectsStaleFencingToken() {
        var repository = Mockito.mock(SimulationActorLeaseRepository.class);
        UUID leaseId = UUID.randomUUID();
        var lease = new SimulationActorLease(leaseId, UUID.randomUUID(), 9L, 4L,
                Instant.now().plusSeconds(10), "ACTIVE");
        when(repository.findById(leaseId)).thenReturn(Optional.of(lease));
        var service = new SimulationLeaseService(repository, 15, true);

        assertThat(service.renew(leaseId, 3L)).isFalse();
        assertThat(service.renew(leaseId, 4L)).isTrue();
    }
}
