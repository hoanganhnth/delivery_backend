package com.delivery.user_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.user.application.api.CreateUserAddressCommand;
import com.delivery.user.application.api.UpdateUserAddressCommand;
import com.delivery.user.application.api.UserAddressResult;
import com.delivery.user.application.api.UserAddressUseCase;
import com.delivery.user.application.api.UserProfileReadUseCase;
import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.payload.BaseResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/addresses")
public class UserAddressController {

    private final UserAddressUseCase userAddressUseCase;
    private final UserProfileReadUseCase userProfileReadUseCase;

    public UserAddressController(
            UserAddressUseCase userAddressUseCase,
            UserProfileReadUseCase userProfileReadUseCase) {
        this.userAddressUseCase = userAddressUseCase;
        this.userProfileReadUseCase = userProfileReadUseCase;
    }

    @GetMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<List<UserAddressResponse>>> getUserAddresses(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (!isSelfOrAdmin(userId, actor)) {
            return forbidden();
        }
        try {
            List<UserAddressResponse> addresses = userAddressUseCase.byUserId(userId).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(new BaseResponse<>(1, addresses));
        } catch (ResponseStatusException denied) {
            return forbidden();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> getAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResult existing = userAddressUseCase.byId(id);
        if (!isSelfOrAdmin(existing.userId(), actor)) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(existing)));
    }

    @PostMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<UserAddressResponse>> createAddress(
            @PathVariable Long userId,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (!isSelfOrAdmin(userId, actor)) {
            return forbidden();
        }
        try {
            UserAddressResult address = userAddressUseCase.create(new CreateUserAddressCommand(
                    userId,
                    request.getLabel(),
                    request.getRecipientName(),
                    request.getPhoneNumber(),
                    request.getAddressLine(),
                    request.getWard(),
                    request.getDistrict(),
                    request.getCity(),
                    request.getPostalCode(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getIsDefault()));
            return ResponseEntity.ok(new BaseResponse<>(1, toResponse(address)));
        } catch (ResponseStatusException denied) {
            return forbidden();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResult existing = userAddressUseCase.byId(id);
        if (!isSelfOrAdmin(existing.userId(), actor)) {
            return forbidden();
        }
        try {
            UserAddressResult address = userAddressUseCase.update(new UpdateUserAddressCommand(
                    id,
                    request.getLabel(),
                    request.getRecipientName(),
                    request.getPhoneNumber(),
                    request.getAddressLine(),
                    request.getWard(),
                    request.getDistrict(),
                    request.getCity(),
                    request.getPostalCode(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getIsDefault()));
            return ResponseEntity.ok(new BaseResponse<>(1, toResponse(address)));
        } catch (ResponseStatusException denied) {
            return forbidden();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        try {
            UserAddressResult existing = userAddressUseCase.byId(id);
            if (!isSelfOrAdmin(existing.userId(), actor)) {
                return ResponseEntity.status(403)
                        .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
            }
            userAddressUseCase.delete(id);
        } catch (ResponseStatusException denied) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
        }
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa địa chỉ thành công"));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<BaseResponse<UserAddressResponse>> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResult existing = userAddressUseCase.byId(id);
        if (!isSelfOrAdmin(existing.userId(), actor)) {
            return forbidden();
        }
        try {
            UserAddressResult address = userAddressUseCase.setDefault(id);
            return ResponseEntity.ok(new BaseResponse<>(1, toResponse(address)));
        } catch (ResponseStatusException denied) {
            return forbidden();
        }
    }

    private boolean isSelfOrAdmin(Long ownerId, AuthenticatedActor actor) {
        if (actor == null) {
            return false;
        }
        if (actor.isAdmin()) {
            return true;
        }
        if (!actor.isUser() || actor.getPrincipalId() == null || ownerId == null) {
            return false;
        }
        return ownerId.equals(userProfileReadUseCase.byPrincipalId(actor.getPrincipalId()).id());
    }

    private UserAddressResponse toResponse(UserAddressResult result) {
        return UserAddressResponse.builder()
                .id(result.id())
                .userId(result.userId())
                .label(result.label())
                .recipientName(result.recipientName())
                .phoneNumber(result.phoneNumber())
                .addressLine(result.addressLine())
                .ward(result.ward())
                .district(result.district())
                .city(result.city())
                .postalCode(result.postalCode())
                .latitude(result.latitude())
                .longitude(result.longitude())
                .isDefault(result.isDefault())
                .createdAt(result.createdAt())
                .updatedAt(result.updatedAt())
                .build();
    }

    private <T> ResponseEntity<BaseResponse<T>> forbidden() {
        return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
    }
}
