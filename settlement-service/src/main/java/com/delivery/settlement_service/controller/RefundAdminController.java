package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.RefundCaseResponse;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.RefundCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Read-only refund queue for authenticated administrators. */
@RestController
@RequestMapping("/api/settlement/admin/refunds")
@RequiredArgsConstructor
public class RefundAdminController {

    private final RefundCaseService refundCaseService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<RefundCaseResponse>>> list(
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }

        RefundStatus parsedStatus;
        try {
            parsedStatus = parseStatus(status);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(exception.getMessage()));
        }
        return ResponseEntity.ok(BaseResponse.success(
                refundCaseService.listAdminCases(parsedStatus, limit)));
    }

    @GetMapping("/{refundId:[0-9a-fA-F-]+}")
    public ResponseEntity<BaseResponse<RefundCaseResponse>> get(
            @RequestHeader(value = "X-Role", required = false) String role,
            @PathVariable UUID refundId) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Only ADMIN can access this endpoint"));
        }
        return ResponseEntity.ok(BaseResponse.success(refundCaseService.getAdminCase(refundId)));
    }

    private RefundStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RefundStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown refund status: " + status);
        }
    }
}
