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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/settlement/balances")
@RequiredArgsConstructor
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
        return ResponseEntity.ok(new BaseResponse<>(1, balance));
    }

    /**
     * Get shipper balance
     */
    @GetMapping("/shipper/{entityId}")
    public ResponseEntity<BaseResponse<BalanceResponse>> getShipperBalance(@PathVariable Long entityId) {
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(new BaseResponse<>(1, balance));
    }

    /**
     * Get total earnings for restaurant
     */
    @GetMapping("/restaurant/{entityId}/earnings")
    public ResponseEntity<BaseResponse<BigDecimal>> getRestaurantEarnings(@PathVariable Long entityId) {
        BigDecimal earnings = balanceService.getTotalEarnings(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(new BaseResponse<>(1, earnings));
    }

    /**
     * Get total earnings for shipper
     */
    @GetMapping("/shipper/{entityId}/earnings")
    public ResponseEntity<BaseResponse<BigDecimal>> getShipperEarnings(@PathVariable Long entityId) {
        BigDecimal earnings = balanceService.getTotalEarnings(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(new BaseResponse<>(1, earnings));
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

        return ResponseEntity.ok(new BaseResponse<>(1, "Withdrawal request submitted",
                transactionMapper.toResponse(transaction)));
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

        return ResponseEntity.ok(new BaseResponse<>(1, "Withdrawal request submitted",
                transactionMapper.toResponse(transaction)));
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

        return ResponseEntity.ok(new BaseResponse<>(1, "Balance held successfully",
                transactionMapper.toResponse(transaction)));
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

        return ResponseEntity.ok(new BaseResponse<>(1, "Balance released successfully",
                transactionMapper.toResponse(transaction)));
    }

    /**
     * ✅ Shipper nạp tiền vào Ví Ký quỹ (Deposit Wallet)
     */
    @PostMapping("/shipper/{entityId}/deposit")
    public ResponseEntity<BaseResponse<TransactionResponse>> topUpDeposit(
            @PathVariable Long entityId,
            @Valid @RequestBody TopUpDepositRequest request) {

        Transaction transaction = transactionService.topUpDeposit(
                entityId, request.getAmount(), request.getPaymentMethod());

        return ResponseEntity.ok(new BaseResponse<>(1, "Deposit topped up successfully",
                transactionMapper.toResponse(transaction)));
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

        return ResponseEntity.ok(new BaseResponse<>(eligible ? 1 : 0, message, eligible));
    }

    /**
     * Recalculate balance from transactions (admin/debug)
     */
    @PostMapping("/restaurant/{entityId}/recalculate")
    public ResponseEntity<BaseResponse<BalanceResponse>> recalculateRestaurantBalance(
            @PathVariable Long entityId,
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can recalculate balances", null));
        }

        balanceService.recalculateBalance(entityId, EntityType.RESTAURANT);
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.RESTAURANT);

        return ResponseEntity.ok(new BaseResponse<>(1, "Balance recalculated", balance));
    }

    /**
     * Recalculate shipper balance from transactions (admin/debug)
     */
    @PostMapping("/shipper/{entityId}/recalculate")
    public ResponseEntity<BaseResponse<BalanceResponse>> recalculateShipperBalance(
            @PathVariable Long entityId,
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can recalculate balances", null));
        }

        balanceService.recalculateBalance(entityId, EntityType.SHIPPER);
        BalanceResponse balance = balanceService.getBalance(entityId, EntityType.SHIPPER);

        return ResponseEntity.ok(new BaseResponse<>(1, "Balance recalculated", balance));
    }
}
