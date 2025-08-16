package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.delivery.user_service.payload.BaseResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;


    @GetMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<List<UserAddressResponse>>> getUserAddresses(@PathVariable Long userId) {
        List<UserAddressResponse> addresses = addressService.getAllAddressesByUser(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, addresses));
    }


    @GetMapping("/addresses/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> getAddress(@PathVariable Long id) {
        UserAddressResponse address = addressService.getAddressById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }


    @PostMapping("/users/{userId}/addresses")
    public ResponseEntity<BaseResponse<UserAddressResponse>> createAddress(@PathVariable Long userId,
            @Valid @RequestBody UserAddressRequest request) {
        UserAddressResponse address = addressService.createAddress(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }


    @PutMapping("/addresses/{id}")
    public ResponseEntity<BaseResponse<UserAddressResponse>> updateAddress(@PathVariable Long id,
            @Valid @RequestBody UserAddressRequest request) {
        UserAddressResponse address = addressService.updateAddress(id, request);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }


    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa địa chỉ thành công"));
    }


    @PatchMapping("/addresses/{id}/default")
    public ResponseEntity<BaseResponse<UserAddressResponse>> setDefault(@PathVariable Long id) {
        UserAddressResponse address = addressService.setDefaultAddress(id);
        return ResponseEntity.ok(new BaseResponse<>(1, address));
    }
}
