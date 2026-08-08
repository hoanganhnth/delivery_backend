package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.request.HoldBalanceRequest;
import com.delivery.settlement_service.dto.request.TopUpDepositRequest;
import com.delivery.settlement_service.dto.request.WithdrawalRequest;
import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.mapper.TransactionMapper;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.BalanceService;
import com.delivery.settlement_service.service.TransactionService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/settlement/balances")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.settlement.self-service-api-enabled", havingValue = "true")
public class BalanceController {

    private final BalanceService balanceService;
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @GetMapping("/restaurant/{entityId}")
    public ResponseEntity<BaseResponse<BalanceResponse>> getRestaurantBalance(@PathVariable Long entityId) {
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(BaseResponse.success(balance));
    }

    @GetMapping("/shipper/{entityId}")
    public ResponseEntity<BaseResponse<BalanceResponse>> getShipperBalance(@PathVariable Long entityId) {
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(BaseResponse.success(balance));
    }

    @GetMapping("/restaurant/{entityId}/earnings")
    public ResponseEntity<BaseResponse<BigDecimal>> getRestaurantEarnings(@PathVariable Long entityId) {
        BigDecimal earnings = balanceService.getTotalEarnings(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(BaseResponse.success(earnings));
    }

    @GetMapping("/shipper/{entityId}/earnings")
    public ResponseEntity<BaseResponse<BigDecimal>> getShipperEarnings(@PathVariable Long entityId) {
        BigDecimal earnings = balanceService.getTotalEarnings(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(BaseResponse.success(earnings));
    }

    @PostMapping("/restaurant/{entityId}/withdraw")
    public ResponseEntity<BaseResponse<TransactionResponse>> requestRestaurantWithdrawal(
            @PathVariable Long entityId,
            @Valid @RequestBody WithdrawalRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        Long userId = actor != null ? actor.getUserId() : null;
        Transaction transaction = transactionService.requestWithdrawal(
                entityId, EntityType.RESTAURANT, request.getAmount(), userId);

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Withdrawal request submitted"));
    }

    @PostMapping("/shipper/{entityId}/withdraw")
    public ResponseEntity<BaseResponse<TransactionResponse>> requestShipperWithdrawal(
            @PathVariable Long entityId,
            @Valid @RequestBody WithdrawalRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        Long userId = actor != null ? actor.getUserId() : null;
        Transaction transaction = transactionService.requestWithdrawal(
                entityId, EntityType.SHIPPER, request.getAmount(), userId);

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Withdrawal request submitted"));
    }

    @PostMapping("/shipper/{entityId}/hold")
    public ResponseEntity<BaseResponse<TransactionResponse>> holdShipperBalance(
            @PathVariable Long entityId,
            @Valid @RequestBody HoldBalanceRequest request) {

        Transaction transaction = transactionService.holdBalance(
                entityId, request.getAmount(), request.getDescription());

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Balance held successfully"));
    }

    @PostMapping("/shipper/{entityId}/release")
    public ResponseEntity<BaseResponse<TransactionResponse>> releaseShipperBalance(
            @PathVariable Long entityId,
            @Valid @RequestBody HoldBalanceRequest request) {

        Transaction transaction = transactionService.releaseBalance(
                entityId, request.getAmount(), request.getDescription());

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Balance released successfully"));
    }

    @PostMapping("/shipper/{entityId}/deposit")
    public ResponseEntity<BaseResponse<TransactionResponse>> topUpDeposit(
            @PathVariable Long entityId,
            @Valid @RequestBody TopUpDepositRequest request) {

        Transaction transaction = transactionService.topUpDeposit(
                entityId, EntityType.SHIPPER, request.getAmount(), request.getPaymentMethod());

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Deposit topped up successfully"));
    }

    @GetMapping("/shipper/{entityId}/cod-eligibility")
    public ResponseEntity<BaseResponse<Boolean>> checkCodEligibility(
            @PathVariable Long entityId,
            @RequestParam BigDecimal codAmount) {

        boolean eligible = transactionService.checkCodEligibility(entityId, codAmount);

        String message = eligible
                ? "Shipper eligible for COD order"
                : "Insufficient deposit balance for COD order";

        return ResponseEntity.ok(BaseResponse.success(eligible, message));
    }
}
