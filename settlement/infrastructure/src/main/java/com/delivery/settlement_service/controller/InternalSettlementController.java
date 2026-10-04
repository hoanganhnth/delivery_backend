package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.service.TransactionService;
import com.delivery.settlement_service.service.CodCapacityHoldService;
import com.delivery.settlement_service.dto.request.CodCapacityHoldRequest;
import com.delivery.settlement_service.entity.CodCapacityHold;
import com.delivery.settlement_service.entity.CodCapacityHoldStatus;
import com.delivery.settlement_service.payload.BaseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/settlement/internal")
public class InternalSettlementController {

    private final TransactionService transactionService;
    private final CodCapacityHoldService codCapacityHoldService;
    private final String internalSecret;

    @Autowired
    public InternalSettlementController(
            TransactionService transactionService,
            CodCapacityHoldService codCapacityHoldService,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.transactionService = transactionService;
        this.codCapacityHoldService = codCapacityHoldService;
        this.internalSecret = internalSecret;
    }

    /** Compatibility constructor for legacy eligibility-only callers. */
    public InternalSettlementController(TransactionService transactionService, String internalSecret) {
        this(transactionService, null, internalSecret);
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

    @PostMapping("/cod-capacity/holds")
    public ResponseEntity<BaseResponse<List<CodCapacityHold>>> createCodCapacityHolds(
            @RequestHeader(value = "Internal-Token", required = false) String internalToken,
            @org.springframework.web.bind.annotation.RequestBody CodCapacityHoldRequest request) {
        if (!authorized(internalToken)) return forbidden();
        if (codCapacityHoldService == null) throw new IllegalStateException("COD hold support is unavailable");
        return ResponseEntity.ok(BaseResponse.success(codCapacityHoldService.hold(request)));
    }

    @PostMapping("/cod-capacity/holds/{holdId}/commit")
    public ResponseEntity<BaseResponse<CodCapacityHold>> commitCodCapacityHold(
            @PathVariable UUID holdId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!authorized(internalToken)) return forbidden();
        if (codCapacityHoldService == null) throw new IllegalStateException("COD hold support is unavailable");
        return ResponseEntity.ok(BaseResponse.success(
                codCapacityHoldService.transition(holdId, CodCapacityHoldStatus.COMMITTED)));
    }

    @PostMapping("/cod-capacity/holds/{holdId}/release")
    public ResponseEntity<BaseResponse<CodCapacityHold>> releaseCodCapacityHold(
            @PathVariable UUID holdId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!authorized(internalToken)) return forbidden();
        if (codCapacityHoldService == null) throw new IllegalStateException("COD hold support is unavailable");
        return ResponseEntity.ok(BaseResponse.success(
                codCapacityHoldService.transition(holdId, CodCapacityHoldStatus.RELEASED)));
    }

    private boolean authorized(String token) {
        return internalSecret != null && !internalSecret.isBlank() && internalSecret.equals(token);
    }

    private <T> ResponseEntity<BaseResponse<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BaseResponse.failure("Forbidden"));
    }
}
