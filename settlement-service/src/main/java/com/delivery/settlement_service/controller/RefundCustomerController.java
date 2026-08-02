package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.RefundCustomerCaseResponse;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.RefundCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer-owned read-only visibility for refund status. */
@RestController
@RequestMapping("/api/settlement/refunds")
@RequiredArgsConstructor
public class RefundCustomerController {

    private final RefundCaseService refundCaseService;

    @GetMapping("/my")
    public ResponseEntity<BaseResponse<List<RefundCustomerCaseResponse>>> list(
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (!"USER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Only USER can access this endpoint"));
        }
        if (userId == null || userId <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(BaseResponse.failure("Authenticated user identity is required"));
        }
        try {
            return ResponseEntity.ok(BaseResponse.success(
                    refundCaseService.listCustomerCases(userId, limit)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(exception.getMessage()));
        }
    }
}
