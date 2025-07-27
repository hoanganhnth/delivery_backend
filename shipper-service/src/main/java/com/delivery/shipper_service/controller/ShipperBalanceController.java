package com.delivery.shipper_service.controller;

import com.delivery.shipper_service.common.constants.ApiPathConstants;
import com.delivery.shipper_service.common.constants.HttpHeaderConstants;
import com.delivery.shipper_service.dto.request.BalanceTransactionRequest;
import com.delivery.shipper_service.dto.response.ShipperBalanceResponse;
import com.delivery.shipper_service.dto.response.ShipperTransactionResponse;
import com.delivery.shipper_service.payload.BaseResponse;
import com.delivery.shipper_service.service.ShipperBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_BALANCES)
public class ShipperBalanceController {

    @Autowired
    private ShipperBalanceService shipperBalanceService;

    @GetMapping
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> getMyBalance(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.getBalanceByShipperId(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> createBalance(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.createBalanceForShipper(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/deposit")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> deposit(
            @RequestBody BalanceTransactionRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.depositBalance(shipperId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> withdraw(
            @RequestBody BalanceTransactionRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.withdrawBalance(shipperId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/hold")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> holdBalance(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.holdBalance(shipperId, amount, description);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/release")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> releaseBalance(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.releaseBalance(shipperId, amount, description);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PostMapping("/earn")
    public ResponseEntity<BaseResponse<ShipperBalanceResponse>> earnFromOrder(
            @RequestParam Long orderId,
            @RequestParam BigDecimal amount,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        ShipperBalanceResponse response = shipperBalanceService.earnFromOrder(shipperId, orderId, amount);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<List<ShipperTransactionResponse>>> getTransactionHistory(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId) {
        List<ShipperTransactionResponse> response = shipperBalanceService.getTransactionHistory(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
}
