package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.UpdateLocationRequest;
import com.delivery.shipper_service.dto.response.ShipperLocationResponse;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.entity.ShipperLocation;
import com.delivery.shipper_service.exception.ResourceNotFoundException;
import com.delivery.shipper_service.mapper.ShipperLocationMapper;
import com.delivery.shipper_service.repository.ShipperLocationRepository;
import com.delivery.shipper_service.repository.ShipperRepository;
import com.delivery.shipper_service.service.ShipperLocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShipperLocationServiceImpl implements ShipperLocationService {

    private final ShipperLocationRepository shipperLocationRepository;
    private final ShipperLocationMapper shipperLocationMapper;
    private final ShipperRepository shipperRepository;

    public ShipperLocationServiceImpl(ShipperLocationRepository shipperLocationRepository,
                                      ShipperLocationMapper shipperLocationMapper,
                                      ShipperRepository shipperRepository) {
        this.shipperLocationRepository = shipperLocationRepository;
        this.shipperLocationMapper = shipperLocationMapper;
        this.shipperRepository = shipperRepository;
    }

    @Override
    public ShipperLocationResponse updateLocationByUserId(Long userId, UpdateLocationRequest request) {
        // Tìm shipper theo userId
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));

        // Find existing location or create new one
        Optional<ShipperLocation> existingLocation = shipperLocationRepository.findByShipperId(shipper.getId());
        ShipperLocation location;

        if (existingLocation.isPresent()) {
            location = existingLocation.get();
            location.setLat(request.getLat());
            location.setLng(request.getLng());
            location.setUpdatedAt(LocalDateTime.now());
        } else {
            location = new ShipperLocation();
            location.setShipperId(shipper.getId());
            location.setLat(request.getLat());
            location.setLng(request.getLng());
            location.setUpdatedAt(LocalDateTime.now());
        }

        ShipperLocation savedLocation = shipperLocationRepository.save(location);
        return shipperLocationMapper.toResponse(savedLocation);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperLocationResponse getLocationByUserId(Long userId) {
        // Tìm shipper theo userId
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));

        ShipperLocation location = shipperLocationRepository.findByShipperId(shipper.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí của shipper"));
        return shipperLocationMapper.toResponse(location);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperLocationResponse> findShippersNearby(Double lat, Double lng, Double radiusKm) {
        List<ShipperLocation> nearbyLocations = shipperLocationRepository.findShippersWithinRadius(lat, lng, radiusKm);
        return nearbyLocations.stream()
                .map(shipperLocationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteLocationByUserId(Long userId) {
        // Tìm shipper theo userId
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));

        shipperLocationRepository.deleteByShipperId(shipper.getId());
    }
}
