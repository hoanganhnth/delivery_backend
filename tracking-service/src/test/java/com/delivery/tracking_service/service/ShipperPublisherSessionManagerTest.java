package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository.ExpiryClaim;
import com.delivery.tracking_service.websocket.PublisherLease;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShipperPublisherSessionManagerTest {

    private final ShipperPublisherLeaseRepository leases = mock(ShipperPublisherLeaseRepository.class);
    private final ShipperAvailabilityService availability = mock(ShipperAvailabilityService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ShipperPublisherSessionManager manager =
            new ShipperPublisherSessionManager(leases, availability, scheduler, 30, 120, 30);

    @Test
    void currentDisconnectMarksOfflineOnlyAfterGraceAndGenerationCheck() {
        PublisherLease lease = new PublisherLease(7L, "session-1", 3L);
        ShipperLocationResponse offline = new ShipperLocationResponse();
        offline.setShipperId(7L);
        @SuppressWarnings("unchecked")
        Consumer<ShipperLocationResponse> callback = mock(Consumer.class);
        ExpiryClaim claim = new ExpiryClaim(lease, 12345L);
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
        verify(callback).accept(offline);
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
