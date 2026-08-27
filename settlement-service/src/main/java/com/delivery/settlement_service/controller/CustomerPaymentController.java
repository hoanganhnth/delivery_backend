package com.delivery.settlement_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.settlement_service.payload.BaseResponse;
import com.delivery.settlement_service.service.CustomerPaymentBoundaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway-only customer adapter for the intentionally incomplete sandbox flow. */
@RestController
@RequestMapping("/api/settlement/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"app.payment.processing-enabled", "app.payment.client-api-enabled"},
        havingValue = "true")
public class CustomerPaymentController {

    private final CustomerPaymentBoundaryService customerPaymentBoundaryService;

    @PostMapping("/create")
    public ResponseEntity<BaseResponse<Void>> create(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestBody CustomerPaymentCreateRequest request) {
        try {
            customerPaymentBoundaryService.createOrderPayment(actor, request == null ? null : request.orderId());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResponse.failure("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED"));
        } catch (CustomerPaymentBoundaryService.CustomerPaymentAccessException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BaseResponse.failure(exception.getMessage()));
        } catch (CustomerPaymentBoundaryService.UnsupportedCustomerPaymentException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(BaseResponse.failure(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(exception.getMessage()));
        }
    }

    @GetMapping("/ref/{paymentRef}")
    public ResponseEntity<BaseResponse<Void>> getByReference(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @PathVariable String paymentRef) {
        try {
            customerPaymentBoundaryService.getByReference(actor, paymentRef);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResponse.failure("CUSTOMER_PAYMENT_OWNERSHIP_UNSUPPORTED"));
        } catch (CustomerPaymentBoundaryService.CustomerPaymentAccessException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BaseResponse.failure(exception.getMessage()));
        } catch (CustomerPaymentBoundaryService.UnsupportedCustomerPaymentException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(BaseResponse.failure(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(exception.getMessage()));
        }
    }

    public record CustomerPaymentCreateRequest(Long orderId) {}
}
