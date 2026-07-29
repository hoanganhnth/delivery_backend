package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;
import com.delivery.user_service.constant.HttpHeaderConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.delivery.user_service.payload.BaseResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;


    @GetMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<List<UserAddressResponse>>> getUserAddresses(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        if (!isSelfOrAdmin(userId, requestUserId, role)) {
            return forbidden();
        }
        List<UserAddressResponse> addresses = addressService.getAllAddressesByUser(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, addresses));
    }


    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> getAddress(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        UserAddressResponse address = addressService.getAddressById(id);
        if (!isSelfOrAdmin(address.getUserId(), requestUserId, role)) {
            return forbidden();
        }
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }


    @PostMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<UserAddressResponse>> createAddress(@PathVariable Long userId,
            @Valid @RequestBody UserAddressRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        if (!isSelfOrAdmin(userId, requestUserId, role)) {
            return forbidden();
        }
        UserAddressResponse address = addressService.createAddress(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }


    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> updateAddress(@PathVariable Long id,
            @Valid @RequestBody UserAddressRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), requestUserId, role)) {
            return forbidden();
        }
        UserAddressResponse address = addressService.updateAddress(id, request);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteAddress(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), requestUserId, role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
        }
        addressService.deleteAddress(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa địa chỉ thành công"));
    }


    @PatchMapping("/{id}/default")
    public ResponseEntity<BaseResponse<UserAddressResponse>> setDefault(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long requestUserId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        UserAddressResponse existing = addressService.getAddressById(id);
        if (!isSelfOrAdmin(existing.getUserId(), requestUserId, role)) {
            return forbidden();
        }
        UserAddressResponse address = addressService.setDefaultAddress(id);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }

    private boolean isSelfOrAdmin(Long ownerId, Long requestUserId, String role) {
        return "ADMIN".equals(role)
                || ("USER".equals(role) && requestUserId != null && requestUserId.equals(ownerId));
    }

    private <T> ResponseEntity<BaseResponse<T>> forbidden() {
        return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập địa chỉ này"));
    }
}
