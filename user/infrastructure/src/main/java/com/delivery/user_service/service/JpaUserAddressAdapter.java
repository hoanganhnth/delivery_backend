package com.delivery.user_service.service;

import com.delivery.user.application.api.CreateUserAddressCommand;
import com.delivery.user.application.api.UpdateUserAddressCommand;
import com.delivery.user.application.api.UserAddressPort;
import com.delivery.user.application.api.UserAddressResult;
import com.delivery.user_service.entity.UserAddress;
import com.delivery.user_service.repository.UserAddressRepository;
import com.delivery.user_service.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** JPA adapter for delivery-address operations. */
@Component
@RequiredArgsConstructor
public class JpaUserAddressAdapter implements UserAddressPort {

    private final UserAddressRepository addresses;
    private final UserRepository users;

    @Override
    public List<UserAddressResult> byUserId(Long userId) {
        return addresses.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100)).stream()
                .map(JpaUserAddressAdapter::toResult)
                .toList();
    }

    @Override
    public UserAddressResult byId(Long id) {
        return toResult(addresses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found")));
    }

    @Override
    @Transactional
    public <T> T withOwnerLock(Long userId, Supplier<T> operation) {
        lockUser(userId);
        return operation.get();
    }

    @Override
    public void clearDefaults(Long userId, Long exceptId) {
        if (exceptId == null) {
            addresses.resetDefaultAddressesForUser(userId);
        } else {
            addresses.resetDefaultAddressesForUserExcept(userId, exceptId);
        }
    }

    @Override
    public Optional<UserAddressResult> latestByUserId(Long userId) {
        return addresses.findFirstByUserIdOrderByCreatedAtDesc(userId).map(JpaUserAddressAdapter::toResult);
    }

    @Override
    public UserAddressResult create(CreateUserAddressCommand command, boolean isDefault) {
        UserAddress address = UserAddress.builder()
                .userId(command.userId())
                .label(command.label())
                .recipientName(command.recipientName())
                .phoneNumber(command.phoneNumber())
                .addressLine(command.addressLine())
                .ward(command.ward())
                .district(command.district())
                .city(command.city())
                .postalCode(command.postalCode())
                .latitude(command.latitude())
                .longitude(command.longitude())
                .isDefault(isDefault)
                .build();
        return toResult(addresses.save(address));
    }

    @Override
    public UserAddressResult update(UpdateUserAddressCommand command, Boolean isDefault) {
        UserAddress address = addresses.findById(command.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        address.setLabel(command.label());
        address.setRecipientName(command.recipientName());
        address.setPhoneNumber(command.phoneNumber());
        address.setAddressLine(command.addressLine());
        address.setWard(command.ward());
        address.setDistrict(command.district());
        address.setCity(command.city());
        address.setPostalCode(command.postalCode());
        address.setLatitude(command.latitude());
        address.setLongitude(command.longitude());
        address.setIsDefault(isDefault);
        return toResult(addresses.save(address));
    }

    @Override
    public void delete(Long id) {
        addresses.deleteById(id);
    }

    @Override
    public UserAddressResult setDefault(Long id) {
        UserAddress address = addresses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        address.setIsDefault(true);
        return toResult(addresses.save(address));
    }

    private void lockUser(Long userId) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static UserAddressResult toResult(UserAddress address) {
        return new UserAddressResult(
                address.getId(),
                address.getUserId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getAddressLine(),
                address.getWard(),
                address.getDistrict(),
                address.getCity(),
                address.getPostalCode(),
                address.getLatitude(),
                address.getLongitude(),
                address.getIsDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
