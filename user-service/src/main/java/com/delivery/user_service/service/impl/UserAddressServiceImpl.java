package com.delivery.user_service.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.entity.UserAddress;
import com.delivery.user_service.repository.UserAddressRepository;
import com.delivery.user_service.service.UserAddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {

    private final UserAddressRepository addressRepository;

    private UserAddressResponse toDto(UserAddress address) {
        return UserAddressResponse.builder()
                .id(address.getId())
                .userId(address.getUserId())
                .label(address.getLabel())
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
        return addressRepository.findByUserId(userId)
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
    public UserAddressResponse createAddress(Long userId, UserAddressRequest req) {
        UserAddress address = UserAddress.builder()
                .userId(userId)
                .label(req.getLabel())
                .addressLine(req.getAddressLine())
                .ward(req.getWard())
                .district(req.getDistrict())
                .city(req.getCity())
                .postalCode(req.getPostalCode())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .isDefault(Boolean.TRUE.equals(req.getIsDefault())) // đảm bảo không null
                .build();
        return toDto(addressRepository.save(address));
    }

    @Override
    public UserAddressResponse updateAddress(Long id, UserAddressRequest req) {
        UserAddress address = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        address.setLabel(req.getLabel());
        address.setAddressLine(req.getAddressLine());
        address.setWard(req.getWard());
        address.setDistrict(req.getDistrict());
        address.setCity(req.getCity());
        address.setPostalCode(req.getPostalCode());
        address.setLatitude(req.getLatitude());
        address.setLongitude(req.getLongitude());

        // ✅ Sửa phần này để tránh unboxing null
        Boolean isDefault = req.getIsDefault();
        if (isDefault != null) {
            address.setIsDefault(isDefault);
        }

        return toDto(addressRepository.save(address));
    }

    @Override
    public void deleteAddress(Long id) {
        if (!addressRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        addressRepository.deleteById(id);
    }

    @Override
    public UserAddressResponse setDefaultAddress(Long id) {
        UserAddress address = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        // Reset tất cả địa chỉ của user về isDefault = false
        List<UserAddress> all = addressRepository.findByUserId(address.getUserId());
        all.forEach(a -> a.setIsDefault(false));

        // Set địa chỉ này là mặc định
        address.setIsDefault(true);

        // Lưu lại tất cả
        addressRepository.saveAll(all);

        return toDto(address);
    }
}
