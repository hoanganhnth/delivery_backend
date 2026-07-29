package com.delivery.user_service.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.entity.UserAddress;
import com.delivery.user_service.repository.UserAddressRepository;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.service.UserAddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    private UserAddressResponse toDto(UserAddress address) {
        return UserAddressResponse.builder()
                .id(address.getId())
                .userId(address.getUserId())
                .label(address.getLabel())
                .recipientName(address.getRecipientName())
                .phoneNumber(address.getPhoneNumber())
                .addressLine(address.getAddressLine())
                .ward(address.getWard())
                .district(address.getDistrict())
                .city(address.getCity())
                .postalCode(address.getPostalCode())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    @Override
    public List<UserAddressResponse> getAllAddressesByUser(Long userId) {
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserAddressResponse getAddressById(Long id) {
        UserAddress address = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        return toDto(address);
    }

    @Override
    @Transactional
    public UserAddressResponse createAddress(Long userId, UserAddressRequest req) {
        lockUser(userId);
        boolean isDefault = Boolean.TRUE.equals(req.getIsDefault());
        
        // Nếu địa chỉ mới là mặc định, reset tất cả địa chỉ khác về false
        if (isDefault) {
            addressRepository.resetDefaultAddressesForUser(userId);
        }
        
        UserAddress address = UserAddress.builder()
                .userId(userId)
                .label(req.getLabel())
                .recipientName(req.getRecipientName())
                .phoneNumber(req.getPhoneNumber())
                .addressLine(req.getAddressLine())
                .ward(req.getWard())
                .district(req.getDistrict())
                .city(req.getCity())
                .postalCode(req.getPostalCode())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .isDefault(isDefault)
                .build();
        return toDto(addressRepository.save(address));
    }

    @Override
    @Transactional
    public UserAddressResponse updateAddress(Long id, UserAddressRequest req) {
        UserAddress address = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        lockUser(address.getUserId());

        address.setLabel(req.getLabel());
        address.setRecipientName(req.getRecipientName());
        address.setPhoneNumber(req.getPhoneNumber());
        address.setAddressLine(req.getAddressLine());
        address.setWard(req.getWard());
        address.setDistrict(req.getDistrict());
        address.setCity(req.getCity());
        address.setPostalCode(req.getPostalCode());
        address.setLatitude(req.getLatitude());
        address.setLongitude(req.getLongitude());

        // Kiểm tra và xử lý địa chỉ mặc định
        Boolean isDefault = req.getIsDefault();
        if (isDefault != null && isDefault) {
            // Nếu đặt làm mặc định, reset tất cả địa chỉ khác về false
            addressRepository.resetDefaultAddressesForUser(address.getUserId());
            address.setIsDefault(true);
        } else if (isDefault != null) {
            address.setIsDefault(isDefault);
        }

        return toDto(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(Long id) {
        UserAddress addressToDelete = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        lockUser(addressToDelete.getUserId());
        
        boolean wasDefault = Boolean.TRUE.equals(addressToDelete.getIsDefault());
        Long userId = addressToDelete.getUserId();
        
        // Xóa địa chỉ
        addressRepository.deleteById(id);
        
        // Nếu địa chỉ vừa xóa là mặc định, đặt địa chỉ đầu tiên (mới nhất) làm mặc định
        if (wasDefault) {
            addressRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).ifPresent(firstAddress -> {
                firstAddress.setIsDefault(true);
                addressRepository.save(firstAddress);
            });
        }
    }

    @Override
    @Transactional
    public UserAddressResponse setDefaultAddress(Long id) {
        UserAddress address = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        lockUser(address.getUserId());

        // Reset tất cả địa chỉ của user về isDefault = false
        addressRepository.resetDefaultAddressesForUser(address.getUserId());

        // Set địa chỉ này là mặc định
        address.setIsDefault(true);
        addressRepository.save(address);

        return toDto(address);
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
