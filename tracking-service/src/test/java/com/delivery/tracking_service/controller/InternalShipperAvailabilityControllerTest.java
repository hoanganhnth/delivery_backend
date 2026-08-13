package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.service.ShipperLocationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalShipperAvailabilityControllerTest {

    private final ShipperLocationService locationService = mock(ShipperLocationService.class);
    private final InternalShipperAvailabilityController controller =
            new InternalShipperAvailabilityController(locationService, "shared-secret");

    @Test
    void failsClosedWithoutTheInternalCredential() {
        var response = controller.markOffline(42L, null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(locationService);
    }

    @Test
    void rejectsInvalidShipperBeforeMutatingTracking() {
        var response = controller.markOffline(0L, "shared-secret");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(locationService);
    }

    @Test
    void marksTheRequestedShipperOfflineWithTheInternalCredential() {
        var response = controller.markOffline(42L, "shared-secret");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(locationService).markShipperOffline(42L);
    }
}
