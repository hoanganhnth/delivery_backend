package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.exception.ResourceNotFoundException;
import com.delivery.shipper_service.mapper.ShipperMapper;
import com.delivery.shipper_service.repository.ShipperRepository;
import com.delivery.shipper_service.service.ShipperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShipperServiceImpl implements ShipperService {

    private final ShipperRepository shipperRepository;
    private final ShipperMapper shipperMapper;

    public ShipperServiceImpl(ShipperRepository shipperRepository,
            ShipperMapper shipperMapper) {
        this.shipperRepository = shipperRepository;
        this.shipperMapper = shipperMapper;
    }

    @Override
    @Transactional
    public ShipperResponse createShipper(CreateShipperRequest request, Long userId, String role) {
        // Kiểm tra trùng lặp license number
        if (shipperRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new IllegalArgumentException("Số bằng lái đã tồn tại trong hệ thống");
        }

        // Kiểm tra trùng lặp ID card
        if (shipperRepository.existsByIdCard(request.getIdCard())) {
            throw new IllegalArgumentException("Số CMND/CCCD đã tồn tại trong hệ thống");
        }

        // Kiểm tra user đã là shipper chưa
        if (shipperRepository.findByUserId(userId).isPresent()) {
            throw new IllegalArgumentException("User này đã là shipper");
        }

        Shipper shipper = shipperMapper.toEntity(request);
        shipper.setUserId(userId); // Set userId từ header
        Shipper savedShipper = shipperRepository.save(shipper);

        // Tự động tạo balance cho shipper mới - sử dụng userId không phải
        // shipper.getId()

        
        return shipperMapper.toResponse(savedShipper);
    }

    @Override
    @Transactional
    public ShipperResponse updateShipperByUserId(Long userId, UpdateShipperRequest request) {
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));

        // Kiểm tra trùng lặp license number (ngoại trừ chính shipper này)
        if (request.getLicenseNumber() != null &&
                !request.getLicenseNumber().equals(shipper.getLicenseNumber()) &&
                shipperRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new IllegalArgumentException("Số bằng lái đã tồn tại trong hệ thống");
        }

        // Kiểm tra trùng lặp ID card (ngoại trừ chính shipper này)
        if (request.getIdCard() != null &&
                !request.getIdCard().equals(shipper.getIdCard()) &&
                shipperRepository.existsByIdCard(request.getIdCard())) {
            throw new IllegalArgumentException("Số CMND/CCCD đã tồn tại trong hệ thống");
        }

        shipperMapper.updateEntityFromRequest(request, shipper);
        Shipper savedShipper = shipperRepository.save(shipper);
        return shipperMapper.toResponse(savedShipper);
    }

    @Override
    @Transactional
    public void deleteShipperByUserId(Long userId) {
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));
        shipperRepository.delete(shipper);
    }

    @Override
    @Transactional
    public ShipperResponse updateOnlineStatusByUserId(Long userId, Boolean isOnline) {
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));

        shipper.setIsOnline(isOnline);
        Shipper savedShipper = shipperRepository.save(shipper);
        return shipperMapper.toResponse(savedShipper);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperResponse getShipperById(Long id) {
        Shipper shipper = shipperRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + id));
        return shipperMapper.toResponse(shipper);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperResponse getShipperByUserId(Long userId) {
        Shipper shipper = shipperRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper của user với ID: " + userId));
        return shipperMapper.toResponse(shipper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperResponse> getAllShippers() {
        List<Shipper> shippers = shipperRepository.findAll();
        return shippers.stream()
                .map(shipperMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperResponse> getOnlineShippers() {
        List<Shipper> onlineShippers = shipperRepository.findByIsOnline(true);
        return onlineShippers.stream()
                .map(shipperMapper::toResponse)
                .collect(Collectors.toList());
    }
}
