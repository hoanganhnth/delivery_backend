package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.UpdateLocationRequest;
import com.delivery.shipper_service.dto.response.ShipperLocationResponse;
import com.delivery.shipper_service.entity.ShipperLocation;
import com.delivery.shipper_service.exception.ResourceNotFoundException;
import com.delivery.shipper_service.mapper.ShipperLocationMapper;
import com.delivery.shipper_service.repository.ShipperLocationRepository;
import com.delivery.shipper_service.service.ShipperLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShipperLocationServiceImpl implements ShipperLocationService {

    private final ShipperLocationRepository shipperLocationRepository;
    private final ShipperLocationMapper shipperLocationMapper;

    public ShipperLocationServiceImpl(ShipperLocationRepository shipperLocationRepository,
                                      ShipperLocationMapper shipperLocationMapper) {
        this.shipperLocationRepository = shipperLocationRepository;
        this.shipperLocationMapper = shipperLocationMapper;
    }

    @Override
    public ShipperLocationResponse updateLocation(Long shipperId, UpdateLocationRequest request) {
        // Find existing location or create new one
        Optional<ShipperLocation> existingLocation = shipperLocationRepository.findByShipperId(shipperId);
        ShipperLocation location;

        if (existingLocation.isPresent()) {
            location = existingLocation.get();
            location.setLat(request.getLat());
            location.setLng(request.getLng());
        } else {
            location = new ShipperLocation(shipperId, request.getLat(), request.getLng());
        }

        ShipperLocation savedLocation = shipperLocationRepository.save(location);
        return shipperLocationMapper.toResponse(savedLocation);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperLocationResponse getLocationByShipperId(Long shipperId) {
        ShipperLocation location = shipperLocationRepository.findByShipperId(shipperId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí của shipper với ID: " + shipperId));
        return shipperLocationMapper.toResponse(location);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperLocationResponse> findShippersNearby(Double lat, Double lng, Double radiusKm) {
        List<ShipperLocation> nearbyLocations = shipperLocationRepository
                .findShippersWithinRadius(lat, lng, radiusKm);
        
        return nearbyLocations.stream()
                .map(shipperLocationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteLocation(Long shipperId) {
        shipperLocationRepository.deleteByShipperId(shipperId);
    }
}
