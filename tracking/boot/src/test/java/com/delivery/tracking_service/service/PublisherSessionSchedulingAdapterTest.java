package com.delivery.tracking_service.service;

import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import com.delivery.tracking.application.DefaultPublisherSessionUseCase;
import com.delivery.tracking.application.api.ShipperAvailabilityUseCase;
import com.delivery.tracking.application.api.PublisherLeaseIncidentPort;
import com.delivery.tracking.domain.PublisherLease;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublisherSessionSchedulingAdapterTest {

    private final ShipperPublisherLeaseRepository leases = mock(ShipperPublisherLeaseRepository.class);
    private final ShipperAvailabilityUseCase availability = mock(ShipperAvailabilityUseCase.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final com.delivery.tracking.application.api.PublisherSessionUseCase manager =
            new DefaultPublisherSessionUseCase(leases, availability,
                    new TaskSchedulerPublisherAdapter(scheduler), mock(PublisherLeaseIncidentPort.class), 30, 120, 30);

    @Test
    void currentDisconnectMarksOfflineOnlyAfterGraceAndGenerationCheck() {
        PublisherLease lease = new PublisherLease(7L, "session-1", 3L);
        PublisherExpiryClaim claim = new PublisherExpiryClaim(lease, 12345L);
        when(leases.releaseForGraceIfCurrent(lease, 30)).thenReturn(true);
        when(leases.claimIfExpired(lease, 30)).thenReturn(claim);
        when(leases.shouldMarkOfflineAfterGrace(lease)).thenReturn(true);
        when(availability.markOfflineIfExpired(claim)).thenReturn(true);

        manager.disconnected(lease);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        verifyNoInteractions(availability);
        task.getValue().run();
        verify(availability).markOfflineIfExpired(claim);
        verify(leases).completeClaim(claim);
    }

    @Test
    void supersededDisconnectCannotScheduleOffline() {
        PublisherLease oldLease = new PublisherLease(7L, "old", 2L);
        when(leases.releaseForGraceIfCurrent(oldLease, 30)).thenReturn(false);

        manager.disconnected(oldLease);

        verifyNoInteractions(scheduler, availability);
    }

    @Test
    void reconnectDuringGraceCancelsOfflineAtGenerationFence() {
        PublisherLease oldLease = new PublisherLease(7L, "old", 2L);
        when(leases.releaseForGraceIfCurrent(oldLease, 30)).thenReturn(true);
        when(leases.claimIfExpired(oldLease, 30)).thenReturn(null);

        manager.disconnected(oldLease);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
        verifyNoInteractions(availability);
        verify(leases, never()).shouldMarkOfflineAfterGrace(any());
    }
}
