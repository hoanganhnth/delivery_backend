package com.delivery.delivery_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.delivery_service.dto.response.DeliveryOfferResponse;
import com.delivery.delivery_service.service.DeliveryService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryControllerOfferAuthorizationTest {

    @Test
    void currentOfferUsesTrustedShipperIdentityWithoutPathOverride() {
        DeliveryService service = mock(DeliveryService.class);
        DeliveryOfferResponse offer = new DeliveryOfferResponse();
        offer.setOrderId(20L);
        when(service.getCurrentOffer(10L, "SHIPPER")).thenReturn(offer);
        AuthenticatedActor actor = new AuthenticatedActor(10L, "shipper@example.com", Set.of("SHIPPER"));

        var response = new DeliveryController(service).getCurrentOffer(actor);

        verify(service).getCurrentOffer(10L, "SHIPPER");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(offer);
    }

    @Test
    void noOfferRemainsAStableSuccessfulRecoveryResponse() {
        DeliveryService service = mock(DeliveryService.class);
        when(service.getCurrentOffer(10L, "SHIPPER")).thenReturn(null);
        AuthenticatedActor actor = new AuthenticatedActor(10L, "shipper@example.com", Set.of("SHIPPER"));

        var response = new DeliveryController(service).getCurrentOffer(actor);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(1);
        assertThat(response.getBody().getData()).isNull();
    }
}
