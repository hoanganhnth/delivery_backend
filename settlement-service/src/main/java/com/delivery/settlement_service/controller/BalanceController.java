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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    /**
     * Get restaurant balance
     */
    @GetMapping("/restaurant/{entityId}")
    public ResponseEntity<BaseResponse<BalanceResponse>> getRestaurantBalance(@PathVariable Long entityId) {
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(BaseResponse.success(balance));
    }

    /**
     * Get shipper balance
     */
    @GetMapping("/shipper/{entityId}")
    public ResponseEntity<BaseResponse<BalanceResponse>> getShipperBalance(@PathVariable Long entityId) {
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(BaseResponse.success(balance));
    }

    /**
     * Get total earnings for restaurant
     */
    @GetMapping("/restaurant/{entityId}/earnings")
    public ResponseEntity<BaseResponse<BigDecimal>> getRestaurantEarnings(@PathVariable Long entityId) {
        BigDecimal earnings = balanceService.getTotalEarnings(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(BaseResponse.success(earnings));
    }

    /**
     * Get total earnings for shipper
     */
    @GetMapping("/shipper/{entityId}/earnings")
    public ResponseEntity<BaseResponse<BigDecimal>> getShipperEarnings(@PathVariable Long entityId) {
        BigDecimal earnings = balanceService.getTotalEarnings(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(BaseResponse.success(earnings));
    }

    /**
     * Request restaurant withdrawal
     */
    @PostMapping("/restaurant/{entityId}/withdraw")
    public ResponseEntity<BaseResponse<TransactionResponse>> requestRestaurantWithdrawal(
            @PathVariable Long entityId,
            @Valid @RequestBody WithdrawalRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        Transaction transaction = transactionService.requestWithdrawal(
                entityId, EntityType.RESTAURANT, request.getAmount(), userId);

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Withdrawal request submitted"));
    }

    /**
     * Request shipper withdrawal
     */
    @PostMapping("/shipper/{entityId}/withdraw")
    public ResponseEntity<BaseResponse<TransactionResponse>> requestShipperWithdrawal(
            @PathVariable Long entityId,
            @Valid @RequestBody WithdrawalRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        Transaction transaction = transactionService.requestWithdrawal(
                entityId, EntityType.SHIPPER, request.getAmount(), userId);

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Withdrawal request submitted"));
    }

    /**
     * Hold shipper balance
     */
    @PostMapping("/shipper/{entityId}/hold")
    public ResponseEntity<BaseResponse<TransactionResponse>> holdShipperBalance(
            @PathVariable Long entityId,
            @Valid @RequestBody HoldBalanceRequest request) {

        Transaction transaction = transactionService.holdBalance(
                entityId, request.getAmount(), request.getDescription());

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Balance held successfully"));
    }

    /**
     * Release shipper balance
     */
    @PostMapping("/shipper/{entityId}/release")
    public ResponseEntity<BaseResponse<TransactionResponse>> releaseShipperBalance(
            @PathVariable Long entityId,
            @Valid @RequestBody HoldBalanceRequest request) {

        Transaction transaction = transactionService.releaseBalance(
                entityId, request.getAmount(), request.getDescription());

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Balance released successfully"));
    }

    /**
     * ✅ Shipper nạp tiền vào Ví Ký quỹ (Deposit Wallet)
     */
    @PostMapping("/shipper/{entityId}/deposit")
    public ResponseEntity<BaseResponse<TransactionResponse>> topUpDeposit(
            @PathVariable Long entityId,
            @Valid @RequestBody TopUpDepositRequest request) {

        Transaction transaction = transactionService.topUpDeposit(
                entityId, EntityType.SHIPPER, request.getAmount(), request.getPaymentMethod());

        return ResponseEntity.ok(BaseResponse.success(
                transactionMapper.toResponse(transaction), "Deposit topped up successfully"));
    }

    /**
     * ✅ Kiểm tra shipper có đủ ký quỹ để nhận đơn COD không
     */
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
