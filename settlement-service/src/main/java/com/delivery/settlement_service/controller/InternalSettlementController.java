package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.service.TransactionService;
import com.delivery.settlement_service.payload.BaseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/settlement/internal")
public class InternalSettlementController {

    private final TransactionService transactionService;
    private final String internalSecret;

    public InternalSettlementController(
            TransactionService transactionService,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.transactionService = transactionService;
        this.internalSecret = internalSecret;
    }

    @GetMapping("/shippers/{shipperId}/cod-eligibility")
    public ResponseEntity<BaseResponse<Boolean>> isCodEligible(
            @PathVariable Long shipperId,
            @RequestParam BigDecimal codAmount,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (internalSecret == null || internalSecret.isBlank()
                || !internalSecret.equals(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Forbidden"));
        }
        if (shipperId == null || shipperId <= 0 || codAmount == null || codAmount.signum() <= 0) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Invalid COD eligibility request"));
        }
        return ResponseEntity.ok(BaseResponse.success(
                transactionService.checkCodEligibility(shipperId, codAmount)));
    }
}
