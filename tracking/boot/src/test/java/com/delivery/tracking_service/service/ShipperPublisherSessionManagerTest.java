package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
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
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShipperPublisherSessionManagerTest {

    private final ShipperPublisherLeaseRepository leases = mock(ShipperPublisherLeaseRepository.class);
    private final ShipperAvailabilityUseCase availability = mock(ShipperAvailabilityUseCase.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ShipperPublisherSessionManager manager =
            new ShipperPublisherSessionManager(new DefaultPublisherSessionUseCase(leases, availability,
                    new TaskSchedulerPublisherAdapter(scheduler), mock(PublisherLeaseIncidentPort.class), 30, 120, 30));

    @Test
    void currentDisconnectMarksOfflineOnlyAfterGraceAndGenerationCheck() {
        PublisherLease lease = new PublisherLease(7L, "session-1", 3L);
        var offline = new com.delivery.tracking.application.api.OfflineShipperLocation(
                new com.delivery.tracking.application.api.CachedShipperLocation(7L, null, null, null, null, null, null),
                java.time.LocalDateTime.of(2026, 10, 3, 7, 0));
        @SuppressWarnings("unchecked")
        Consumer<ShipperLocationResponse> callback = mock(Consumer.class);
        PublisherExpiryClaim claim = new PublisherExpiryClaim(lease, 12345L);
        when(leases.releaseForGraceIfCurrent(lease, 30)).thenReturn(true);
        when(leases.claimIfExpired(lease, 30)).thenReturn(claim);
        when(leases.shouldMarkOfflineAfterGrace(lease)).thenReturn(true);
        when(availability.markOffline(7L)).thenReturn(offline);

        manager.disconnected(lease, callback);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        verifyNoInteractions(availability, callback);
        task.getValue().run();
        verify(availability).markOffline(7L);
        var result = ArgumentCaptor.forClass(ShipperLocationResponse.class);
        verify(callback).accept(result.capture());
        org.assertj.core.api.Assertions.assertThat(result.getValue().getShipperId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(result.getValue().getIsOnline()).isFalse();
        org.assertj.core.api.Assertions.assertThat(result.getValue().getUpdatedAt()).isEqualTo(offline.timestamp().toString());
        verify(leases).completeClaim(claim);
    }

    @Test
    void supersededDisconnectCannotScheduleOffline() {
        PublisherLease oldLease = new PublisherLease(7L, "old", 2L);
        @SuppressWarnings("unchecked")
        Consumer<ShipperLocationResponse> callback = mock(Consumer.class);
        when(leases.releaseForGraceIfCurrent(oldLease, 30)).thenReturn(false);

        manager.disconnected(oldLease, callback);

        verifyNoInteractions(scheduler, availability, callback);
    }

    @Test
    void reconnectDuringGraceCancelsOfflineAtGenerationFence() {
        PublisherLease oldLease = new PublisherLease(7L, "old", 2L);
        @SuppressWarnings("unchecked")
        Consumer<ShipperLocationResponse> callback = mock(Consumer.class);
        when(leases.releaseForGraceIfCurrent(oldLease, 30)).thenReturn(true);
        when(leases.claimIfExpired(oldLease, 30)).thenReturn(null);

        manager.disconnected(oldLease, callback);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
        verifyNoInteractions(availability, callback);
        verify(leases, never()).shouldMarkOfflineAfterGrace(any());
    }
}
