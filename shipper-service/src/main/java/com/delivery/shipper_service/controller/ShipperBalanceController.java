package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.HttpHeaderConstants;
import com.delivery.shipper_service.dto.request.BalanceTransactionRequest;
import com.delivery.shipper_service.dto.response.ShipperBalanceResponse;
import com.delivery.shipper_service.dto.response.ShipperTransactionResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.service.ShipperBalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_BALANCES)
public class ShipperBalanceController {

    private final ShipperBalanceService shipperBalanceService;

    public ShipperBalanceController(ShipperBalanceService shipperBalanceService) {
        this.shipperBalanceService = shipperBalanceService;
    }

    @GetMapping
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> getMyBalance(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.getBalanceByUserId(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> createBalance(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.createBalanceForUserId(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/deposit")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> deposit(
            @RequestBody BalanceTransactionRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.depositBalanceByUserId(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> withdraw(
            @RequestBody BalanceTransactionRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.withdrawBalanceByUserId(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/hold")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> holdBalance(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.holdBalanceByUserId(userId, amount, description);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/release")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> releaseBalance(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.releaseBalanceByUserId(userId, amount, description);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/earn")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> earnFromOrder(
            @RequestParam Long orderId,
            @RequestParam BigDecimal amount,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        ShipperBalanceResponse response = shipperBalanceService.earnFromOrderByUserId(userId, orderId, amount);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<List<ShipperTransactionResponse>>> getTransactionHistory(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        List<ShipperTransactionResponse> response = shipperBalanceService.getTransactionHistoryByUserId(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
}
