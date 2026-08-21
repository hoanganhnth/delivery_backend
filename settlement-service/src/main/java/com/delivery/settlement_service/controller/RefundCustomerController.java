package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.RefundCustomerCaseResponse;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.RefundCaseService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settlement/refunds")
@RequiredArgsConstructor
public class RefundCustomerController {

    private final RefundCaseService refundCaseService;

    @GetMapping("/my")
    public ResponseEntity<BaseResponse<List<RefundCustomerCaseResponse>>> list(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (actor == null || !actor.isUser()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Only USER can access this endpoint"));
        }
        if (actor.getPrincipalId() == null || actor.getPrincipalId() <= 0
                || actor.getLegacyUserId() == null || actor.getLegacyUserId() <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(BaseResponse.failure("Authenticated user identity is required"));
        }
        try {
            return ResponseEntity.ok(BaseResponse.success(
                    refundCaseService.listCustomerCases(actor.getPrincipalId(), actor.getLegacyUserId(), limit)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(exception.getMessage()));
        }
    }
}
