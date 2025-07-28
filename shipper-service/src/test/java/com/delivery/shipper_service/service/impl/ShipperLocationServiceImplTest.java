package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.UpdateLocationRequest;
import com.delivery.shipper_service.dto.response.ShipperLocationResponse;
import com.delivery.shipper_service.entity.ShipperLocation;
import com.delivery.shipper_service.mapper.ShipperLocationMapper;
import com.delivery.shipper_service.repository.ShipperLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShipperLocationServiceImplTest {

    @Mock
    private ShipperLocationRepository shipperLocationRepository;

    @Mock
    private ShipperLocationMapper shipperLocationMapper;

    @InjectMocks
    private ShipperLocationServiceImpl shipperLocationService;

    @Test
    public void testUpdateLocationSuccess() {
        // Given
        Long shipperId = 1L;
        UpdateLocationRequest request = new UpdateLocationRequest(10.123, 106.456);
        
        ShipperLocation existingLocation = new ShipperLocation(shipperId, 10.111, 106.222);
        ShipperLocation updatedLocation = new ShipperLocation(shipperId, request.getLat(), request.getLng());
        
        ShipperLocationResponse expectedResponse = new ShipperLocationResponse();
        expectedResponse.setShipperId(shipperId);
        expectedResponse.setLat(request.getLat());
        expectedResponse.setLng(request.getLng());

        when(shipperLocationRepository.findByShipperId(shipperId)).thenReturn(Optional.of(existingLocation));
        when(shipperLocationRepository.save(any(ShipperLocation.class))).thenReturn(updatedLocation);
        when(shipperLocationMapper.toResponse(any(ShipperLocation.class))).thenReturn(expectedResponse);

        // When
        ShipperLocationResponse result = shipperLocationService.updateLocationByUserId(shipperId, request);

        // Then
        assertNotNull(result);
        assertEquals(shipperId, result.getShipperId());
        assertEquals(request.getLat(), result.getLat());
        assertEquals(request.getLng(), result.getLng());
        
        verify(shipperLocationRepository).findByShipperId(shipperId);
        verify(shipperLocationRepository).save(any(ShipperLocation.class));
        verify(shipperLocationMapper).toResponse(any(ShipperLocation.class));
    }
}
