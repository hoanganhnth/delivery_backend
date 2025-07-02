package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;

    @GetMapping("/users/{userId}/addresses")
    public ResponseEntity<List<UserAddressResponse>> getUserAddresses(@PathVariable Long userId) {
        return ResponseEntity.ok(addressService.getAllAddressesByUser(userId));
    }

    @GetMapping("/addresses/{id}")
    public ResponseEntity<UserAddressResponse> getAddress(@PathVariable Long id) {
        return ResponseEntity.ok(addressService.getAddressById(id));
    }

    @PostMapping("/users/{userId}/addresses")
    public ResponseEntity<UserAddressResponse> createAddress(@PathVariable Long userId,
            @Valid @RequestBody UserAddressRequest request) {
        return ResponseEntity.ok(addressService.createAddress(userId, request));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<UserAddressResponse> updateAddress(@PathVariable Long id,
            @Valid @RequestBody UserAddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(id, request));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/addresses/{id}/default")
    public ResponseEntity<UserAddressResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(addressService.setDefaultAddress(id));
    }
}
