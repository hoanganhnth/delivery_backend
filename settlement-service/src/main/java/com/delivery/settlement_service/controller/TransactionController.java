package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlement/transactions")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.settlement.self-service-api-enabled", havingValue = "true")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Get restaurant transaction history
     */
    @GetMapping("/restaurant/{entityId}")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getRestaurantTransactions(
            @PathVariable Long entityId) {

        List<TransactionResponse> transactions = transactionService.getTransactions(entityId, EntityType.RESTAURANT);
        return ResponseEntity.ok(BaseResponse.success(transactions));
    }

    /**
     * Get shipper transaction history
     */
    @GetMapping("/shipper/{entityId}")
    public ResponseEntity<BaseResponse<List<TransactionResponse>>> getShipperTransactions(
            @PathVariable Long entityId) {

        List<TransactionResponse> transactions = transactionService.getTransactions(entityId, EntityType.SHIPPER);
        return ResponseEntity.ok(BaseResponse.success(transactions));
    }

    /**
     * Get transaction by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<TransactionResponse>> getTransactionById(@PathVariable Long id) {
        TransactionResponse transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(BaseResponse.success(transaction));
    }
}
