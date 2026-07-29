package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.BalanceResponse;
import com.delivery.settlement_service.dto.response.TransactionResponse;
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

    /**
     * Get all balances
     */
    @GetMapping("/balances")
    public ResponseEntity<BaseResponse<List<BalanceResponse>>> getAllBalances(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        List<BalanceResponse> balances = balanceService.getAllBalances();
        return ResponseEntity.ok(BaseResponse.success(balances));
    }

    /**
     * Get all transactions
     */
    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getAllTransactions(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        List<TransactionResponse> transactions = transactionService.getAllTransactions();
        return ResponseEntity.ok(BaseResponse.success(transactions));
    }

    /**
     * Get pending withdrawals
     */
    @GetMapping("/transactions/pending")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getPendingWithdrawals(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        List<TransactionResponse> pendingWithdrawals = transactionService.getPendingWithdrawals();
        return ResponseEntity.ok(BaseResponse.success(pendingWithdrawals));
    }

    /**
     * Get total platform revenue (commission)
     */
    @GetMapping("/revenue")
    public ResponseEntity<BaseResponse<BigDecimal>> getPlatformRevenue(
            @RequestHeader(value = "X-Role", required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        BigDecimal revenue = transactionRepository.calculateTotalPlatformRevenue();
        return ResponseEntity.ok(BaseResponse.success(revenue, "Total platform revenue"));
    }

}
