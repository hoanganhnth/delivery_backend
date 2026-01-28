package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlement/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Get restaurant transaction history
     */
    @GetMapping("/restaurant/{entityId}")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getRestaurantTransactions(
            @PathVariable Long entityId) {

        List<TransactionResponse> transactions = transactionService.getTransactions(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(new BaseResponse<>(1, transactions));
    }

    /**
     * Get shipper transaction history
     */
    @GetMapping("/shipper/{entityId}")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getShipperTransactions(
            @PathVariable Long entityId) {

        List<TransactionResponse> transactions = transactionService.getTransactions(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(new BaseResponse<>(1, transactions));
    }

    /**
     * Get transaction by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<TransactionResponse>> getTransactionById(@PathVariable Long id) {
        TransactionResponse transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, transaction));
    }
}
