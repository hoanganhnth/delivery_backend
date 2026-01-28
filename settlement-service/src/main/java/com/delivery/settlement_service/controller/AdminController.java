package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.request.RejectWithdrawalRequest;
import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.mapper.TransactionMapper;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.BalanceService;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/settlement/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BalanceService balanceService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    /**
     * Get all balances
     */
    @GetMapping("/balances")
    public ResponseEntity<BaseResponse<List<BalanceResponse>>> getAllBalances(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can access this endpoint", null));
        }

        List<BalanceResponse> balances = balanceService.getAllBalances();
        return ResponseEntity.ok(new BaseResponse<>(1, balances));
    }

    /**
     * Get all transactions
     */
    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getAllTransactions(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can access this endpoint", null));
        }

        List<TransactionResponse> transactions = transactionService.getAllTransactions();
        return ResponseEntity.ok(new BaseResponse<>(1, transactions));
    }

    /**
     * Get pending withdrawals
     */
    @GetMapping("/transactions/pending")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getPendingWithdrawals(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can access this endpoint", null));
        }

        List<TransactionResponse> pendingWithdrawals = transactionService.getPendingWithdrawals();
        return ResponseEntity.ok(new BaseResponse<>(1, pendingWithdrawals));
    }

    /**
     * Approve withdrawal
     */
    @PostMapping("/transactions/{id}/approve")
    public ResponseEntity<BaseResponse<TransactionResponse>> approveWithdrawal(
            @PathVariable Long id,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can approve withdrawals", null));
        }

        Transaction transaction = transactionService.approveWithdrawal(id, adminId);
        return ResponseEntity.ok(new BaseResponse<>(1, "Withdrawal approved",
                transactionMapper.toResponse(transaction)));
    }

    /**
     * Reject withdrawal
     */
    @PostMapping("/transactions/{id}/reject")
    public ResponseEntity<BaseResponse<TransactionResponse>> rejectWithdrawal(
            @PathVariable Long id,
            @RequestBody(required = false) RejectWithdrawalRequest request,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can reject withdrawals", null));
        }

        Transaction transaction = transactionService.rejectWithdrawal(id, adminId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, "Withdrawal rejected",
                transactionMapper.toResponse(transaction)));
    }

    /**
     * Get total platform revenue (commission)
     */
    @GetMapping("/revenue")
    public ResponseEntity<BaseResponse<BigDecimal>> getPlatformRevenue(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can access this endpoint", null));
        }

        BigDecimal revenue = transactionRepository.calculateTotalPlatformRevenue();
        return ResponseEntity.ok(new BaseResponse<>(1, "Total platform revenue", revenue));
    }

    /**
     * Reverse a transaction
     */
    @PostMapping("/transactions/{id}/reverse")
    public ResponseEntity<BaseResponse<TransactionResponse>> reverseTransaction(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, "Only ADMIN can reverse transactions", null));
        }

        Transaction transaction = transactionService.reverseTransaction(id, adminId, reason);
        return ResponseEntity.ok(new BaseResponse<>(1, "Transaction reversed",
                transactionMapper.toResponse(transaction)));
    }
}
