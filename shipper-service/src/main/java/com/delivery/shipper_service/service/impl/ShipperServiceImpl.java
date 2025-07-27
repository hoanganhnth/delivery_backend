package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.common.constants.RoleConstants;
import com.delivery.shipper_service.dto.request.CreateShipperRequest;
import com.delivery.shipper_service.dto.request.UpdateShipperRequest;
import com.delivery.shipper_service.dto.response.ShipperResponse;
import com.delivery.shipper_service.entity.Shipper;
import com.delivery.shipper_service.exception.ResourceNotFoundException;
import com.delivery.shipper_service.mapper.ShipperMapper;
import com.delivery.shipper_service.repository.ShipperRepository;
import com.delivery.shipper_service.service.ShipperService;
import com.delivery.shipper_service.service.ShipperBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShipperServiceImpl implements ShipperService {

    @Autowired
    private ShipperRepository shipperRepository;

    @Autowired
    private ShipperMapper shipperMapper;

    @Autowired
    private ShipperBalanceService shipperBalanceService;

    @Override
    public ShipperResponse createShipper(CreateShipperRequest request, Long creatorId, String role) {
        // Kiểm tra quyền tạo shipper
        if (!RoleConstants.ADMIN.equals(role) && !request.getUserId().equals(creatorId)) {
            throw new AccessDeniedException("Bạn không có quyền tạo shipper cho user khác");
        }

        // Kiểm tra trùng lặp license number
        if (shipperRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new IllegalArgumentException("Số bằng lái đã tồn tại trong hệ thống");
        }

        // Kiểm tra trùng lặp ID card
        if (shipperRepository.existsByIdCard(request.getIdCard())) {
            throw new IllegalArgumentException("Số CMND/CCCD đã tồn tại trong hệ thống");
        }

        // Kiểm tra user đã là shipper chưa
        if (shipperRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new IllegalArgumentException("User này đã là shipper");
        }

        Shipper shipper = shipperMapper.toEntity(request);
        Shipper savedShipper = shipperRepository.save(shipper);
        
        // Automatically create balance for new shipper
        shipperBalanceService.createBalanceForShipper(savedShipper.getId());
        
        return shipperMapper.toResponse(savedShipper);
    }

    @Override
    public ShipperResponse updateShipper(Long id, UpdateShipperRequest request, Long creatorId) {
        Shipper shipper = shipperRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + id));

        // Kiểm tra quyền cập nhật
        if (!shipper.getUserId().equals(creatorId)) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật thông tin shipper này");
        }

        // Kiểm tra trùng lặp license number (nếu có thay đổi)
        if (request.getLicenseNumber() != null && 
            !request.getLicenseNumber().equals(shipper.getLicenseNumber()) &&
            shipperRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new IllegalArgumentException("Số bằng lái đã tồn tại trong hệ thống");
        }

        // Kiểm tra trùng lặp ID card (nếu có thay đổi)
        if (request.getIdCard() != null && 
            !request.getIdCard().equals(shipper.getIdCard()) &&
            shipperRepository.existsByIdCard(request.getIdCard())) {
            throw new IllegalArgumentException("Số CMND/CCCD đã tồn tại trong hệ thống");
        }

        shipperMapper.updateEntityFromRequest(request, shipper);
        Shipper updatedShipper = shipperRepository.save(shipper);
        return shipperMapper.toResponse(updatedShipper);
    }

    @Override
    public void deleteShipper(Long id, Long creatorId) {
        Shipper shipper = shipperRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + id));

        // Chỉ cho phép shipper xóa chính mình
        if (!shipper.getUserId().equals(creatorId)) {
            throw new AccessDeniedException("Bạn không có quyền xóa shipper này");
        }

        shipperRepository.delete(shipper);
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper cho user ID: " + userId));
        return shipperMapper.toResponse(shipper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperResponse> getAllShippers() {
        return shipperRepository.findAll()
                .stream()
                .map(shipperMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperResponse> getOnlineShippers() {
        return shipperRepository.findByIsOnline(true)
                .stream()
                .map(shipperMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ShipperResponse updateOnlineStatus(Long id, Boolean isOnline, Long creatorId) {
        Shipper shipper = shipperRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + id));

        // Kiểm tra quyền cập nhật
        if (!shipper.getUserId().equals(creatorId)) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật trạng thái online của shipper này");
        }

        shipper.setIsOnline(isOnline);
        Shipper updatedShipper = shipperRepository.save(shipper);
        return shipperMapper.toResponse(updatedShipper);
    }
}
