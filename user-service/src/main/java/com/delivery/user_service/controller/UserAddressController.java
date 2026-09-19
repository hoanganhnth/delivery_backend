package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;
import com.delivery.user_service.service.UserService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.delivery.user_service.payload.BaseResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;
    private final UserService userService;

    @GetMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<List<UserAddressResponse>>> getUserAddresses(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        List<UserAddressResponse> addresses;
        if (!isSelfOrAdmin(userId, actor)) return forbidden();
        try {
            addresses = addressService.getAllAddressesByUser(userId, actor);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, addresses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> getAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse address;
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), actor)) return forbidden();
        try {
            address = addressService.getAddressById(id, actor);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    @PostMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<UserAddressResponse>> createAddress(
            @PathVariable Long userId,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse address;
        if (!isSelfOrAdmin(userId, actor)) return forbidden();
        try {
            address = addressService.createAddress(userId, request, actor);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse address;
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), actor)) return forbidden();
        try {
            address = addressService.updateAddress(id, request, actor);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        try {
            UserAddressResponse existing = addressService.getAddressById(id);
            if (!isSelfOrAdmin(existing.getUserId(), actor)) {
                return ResponseEntity.status(403)
                        .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
            }
            addressService.deleteAddress(id, actor);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
        }
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa địa chỉ thành công"));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<BaseResponse<UserAddressResponse>> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        UserAddressResponse address;
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), actor)) return forbidden();
        try {
            address = addressService.setDefaultAddress(id, actor);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    private boolean isSelfOrAdmin(Long ownerId, AuthenticatedActor actor) {
        if (actor == null) return false;
        if (actor.isAdmin()) return true;
        if (!actor.isUser() || actor.getPrincipalId() == null || ownerId == null) return false;
        return ownerId.equals(userService.getUserByPrincipalId(actor.getPrincipalId()).getId());
    }

    private <T> ResponseEntity<BaseResponse<T>> forbidden() {
        return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
    }
}
