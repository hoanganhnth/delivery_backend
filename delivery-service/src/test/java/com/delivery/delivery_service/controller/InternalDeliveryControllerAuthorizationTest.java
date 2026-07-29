package com.delivery.delivery_service.controller;

import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InternalDeliveryControllerAuthorizationTest {

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final InternalDeliveryController controller =
            new InternalDeliveryController(repository, "shared-secret");

    @Test
    void failsClosedWithoutSharedSecret() {
        var response = controller.canTrack(1L, 7L, "USER", 42L, null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getStatus()).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    void customerCanTrackOnlyOwnActiveDeliveryAndAssignedShipper() {
        Delivery delivery = new Delivery();
        delivery.setId(1L);
        delivery.setCreatorId(7L);
        delivery.setShipperId(42L);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        when(repository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThat(controller.canTrack(1L, 7L, "USER", 42L, "shared-secret").getBody().getData())
                .isEqualTo(Boolean.TRUE);
        assertThat(controller.canTrack(1L, 9L, "USER", 42L, "shared-secret").getBody().getData())
                .isEqualTo(Boolean.FALSE);
        assertThat(controller.canTrack(1L, 7L, "USER", 99L, "shared-secret").getBody().getData())
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void terminalDeliveryCannotBeTracked() {
        Delivery delivery = new Delivery();
        delivery.setId(1L);
        delivery.setCreatorId(7L);
        delivery.setShipperId(42L);
        delivery.setStatus(DeliveryStatus.DELIVERED);
        when(repository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThat(controller.canTrack(1L, 7L, "USER", 42L, "shared-secret").getBody().getData())
                .isEqualTo(Boolean.FALSE);
    }
}
