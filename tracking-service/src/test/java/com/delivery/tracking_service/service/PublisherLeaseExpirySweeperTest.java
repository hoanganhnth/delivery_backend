package com.delivery.tracking_service.service;

import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository.ExpiryClaim;
import com.delivery.tracking_service.websocket.PublisherLease;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PublisherLeaseExpirySweeperTest {

    private final ShipperPublisherLeaseRepository leases =
            mock(ShipperPublisherLeaseRepository.class);
    private final ShipperAvailabilityService availability =
            mock(ShipperAvailabilityService.class);
    private final PublisherLeaseExpirySweeper sweeper =
            new PublisherLeaseExpirySweeper(leases, availability);
    private final PublisherLease lease = new PublisherLease(7L, "session-1", 3L);
    private final ExpiryClaim claim = new ExpiryClaim(lease, 12345L);

    @Test
    void expiredDisconnectedLeasePublishesOfflineThenCompletesClaim() {
        when(leases.claimExpired(anyInt(), anyLong())).thenReturn(List.of(claim));
        when(leases.shouldMarkOfflineAfterGrace(lease)).thenReturn(true);

        sweeper.sweep();

        verify(availability).markOffline(7L);
        verify(leases).completeClaim(claim);
    }

    @Test
    void refreshedOrSupersededLeaseCompletesWithoutOffline() {
        when(leases.claimExpired(anyInt(), anyLong())).thenReturn(List.of(claim));
        when(leases.shouldMarkOfflineAfterGrace(lease)).thenReturn(false);

        sweeper.sweep();

        verifyNoInteractions(availability);
        verify(leases).completeClaim(claim);
    }

    @Test
    void failedOfflinePublishLeavesClaimForRetry() {
        when(leases.claimExpired(anyInt(), anyLong())).thenReturn(List.of(claim));
        when(leases.shouldMarkOfflineAfterGrace(lease)).thenReturn(true);
        when(availability.markOffline(7L)).thenThrow(new IllegalStateException("broker unavailable"));

        sweeper.sweep();

        verify(leases, never()).completeClaim(any());
    }
}
