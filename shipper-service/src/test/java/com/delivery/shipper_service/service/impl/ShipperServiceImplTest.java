package com.delivery.shipper_service.service.impl;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class ShipperServiceImplTest {

    @Mock
    private ShipperRepository shipperRepository;

    @Mock
    private ShipperMapper shipperMapper;

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

        verifyNoInteractions(shipperRepository, shipperMapper);
    }
}
