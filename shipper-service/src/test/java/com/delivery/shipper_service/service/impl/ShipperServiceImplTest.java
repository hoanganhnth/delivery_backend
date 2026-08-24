package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.client.TrackingAvailabilityClient;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.mapper.ShipperMapper;
import com.delivery.shipper_service.repository.ShipperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class ShipperServiceImplTest {

    @Mock
    private ShipperRepository shipperRepository;

    @Mock
    private ShipperMapper shipperMapper;

    @Mock
    private TrackingAvailabilityClient trackingAvailabilityClient;

    @InjectMocks
    private ShipperServiceImpl shipperService;

    @Test
    public void testGetShipperById() {
        // Given
        Long shipperId = 1L;
        Shipper shipper = new Shipper();
        shipper.setId(shipperId);
        shipper.setUserId(1L);
        shipper.setVehicleType("MOTORBIKE");
        shipper.setIsOnline(true);

        ShipperResponse expectedResponse = new ShipperResponse();
        expectedResponse.setId(shipperId);
        expectedResponse.setUserId(1L);
        expectedResponse.setVehicleType("MOTORBIKE");
        expectedResponse.setIsOnline(true);

        // When
        when(shipperRepository.findById(shipperId)).thenReturn(Optional.of(shipper));
        when(shipperMapper.toResponse(shipper)).thenReturn(expectedResponse);

        // Then
        ShipperResponse result = shipperService.getShipperById(shipperId);
        assertEquals(shipperId, result.getId());
        assertEquals("MOTORBIKE", result.getVehicleType());
        assertTrue(result.getIsOnline());
    }

    @Test
    void onlineCompatibilityListUsesBoundedRepositoryQuery() {
        when(shipperRepository.findByIsOnline(eq(true), any(Pageable.class))).thenReturn(List.of());

        assertTrue(shipperService.getOnlineShippers().isEmpty());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(shipperRepository).findByIsOnline(eq(true), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void createShipperRejectsNullIdentityBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> shipperService.createShipper(null, null, "SHIPPER"));

        verifyNoInteractions(shipperRepository, shipperMapper);
    }

    @Test
    void updateOnlineStatusRejectsNullFlagBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> shipperService.updateOnlineStatusByUserId(7L, null));

        verifyNoInteractions(shipperRepository, shipperMapper, trackingAvailabilityClient);
    }

    @Test
    void offlineStatusPublishesTrackingTombstoneBeforeSavingProfileProjection() {
        Shipper shipper = new Shipper();
        shipper.setId(7L);
        shipper.setUserId(107L);
        ShipperResponse response = new ShipperResponse();
        response.setIsOnline(false);
        when(shipperRepository.findByUserId(107L)).thenReturn(Optional.of(shipper));
        when(shipperRepository.save(shipper)).thenReturn(shipper);
        when(shipperMapper.toResponse(shipper)).thenReturn(response);

        shipperService.updateOnlineStatusByUserId(107L, false);

        var ordered = inOrder(trackingAvailabilityClient, shipperRepository);
        ordered.verify(trackingAvailabilityClient).markOffline(7L);
        ordered.verify(shipperRepository).save(shipper);
    }

    @Test
    void offlineStatusDoesNotSaveProfileWhenTrackingRejectsTombstone() {
        Shipper shipper = new Shipper();
        shipper.setId(7L);
        shipper.setUserId(107L);
        when(shipperRepository.findByUserId(107L)).thenReturn(Optional.of(shipper));
        org.mockito.Mockito.doThrow(new IllegalStateException("Tracking unavailable"))
                .when(trackingAvailabilityClient).markOffline(7L);

        assertThrows(IllegalStateException.class,
                () -> shipperService.updateOnlineStatusByUserId(107L, false));

        verify(shipperRepository, never()).save(any());
    }

    @Test
    void onlineStatusOnlyUpdatesPublisherIntent() {
        Shipper shipper = new Shipper();
        shipper.setId(7L);
        shipper.setUserId(107L);
        ShipperResponse response = new ShipperResponse();
        response.setIsOnline(true);
        when(shipperRepository.findByUserId(107L)).thenReturn(Optional.of(shipper));
        when(shipperRepository.save(shipper)).thenReturn(shipper);
        when(shipperMapper.toResponse(shipper)).thenReturn(response);

        shipperService.updateOnlineStatusByUserId(107L, true);

        verifyNoInteractions(trackingAvailabilityClient);
        verify(shipperRepository).save(shipper);
    }

    @Test
    void genericProfileUpdateRejectsAvailabilityMutationBeforeRepositoryAccess() {
        UpdateShipperRequest request = new UpdateShipperRequest();
        request.setIsOnline(false);

        assertThrows(IllegalArgumentException.class,
                () -> shipperService.updateShipperByUserId(7L, request));

        verifyNoInteractions(shipperRepository, shipperMapper, trackingAvailabilityClient);
    }
}
