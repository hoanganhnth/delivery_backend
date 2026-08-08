package com.delivery.shipper_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.shipper_service.service.IShipperRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShipperRatingControllerAuthorizationTest {

    @Test
    void selfRatingsRequireShipperRoleBeforeServiceLookup() {
        IShipperRatingService service = mock(IShipperRatingService.class);
        ShipperRatingController controller = new ShipperRatingController(service);
        AuthenticatedActor userActor = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));

        assertThatThrownBy(() -> controller.getMyRatings(userActor))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    void shipperReadsRatingsByTrustedUserIdentity() {
        IShipperRatingService service = mock(IShipperRatingService.class);
        when(service.getMyRatings(7L)).thenReturn(List.of());
        ShipperRatingController controller = new ShipperRatingController(service);
        AuthenticatedActor shipperActor = new AuthenticatedActor(7L, "shipper@example.com", Set.of("SHIPPER"));

        var response = controller.getMyRatings(shipperActor);

        verify(service).getMyRatings(7L);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(1);
        assertThat(response.getBody().getData()).isEmpty();
    }
}
