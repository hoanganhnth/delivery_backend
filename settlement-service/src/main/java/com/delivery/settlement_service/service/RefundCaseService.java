package com.delivery.settlement_service.service;

import com.delivery.settlement.domain.refund.RefundPolicy;
import com.delivery.settlement_service.adapter.JpaRefundCaseAdapter;
import com.delivery.settlement.application.refund.DefaultRefundCaseUseCase;
import com.delivery.settlement.application.refund.DefaultRefundQueryUseCase;
import com.delivery.settlement.application.refund.RefundCaseMissing;
import com.delivery.settlement.application.api.refund.AdminRefundCase;
import com.delivery.settlement.application.api.refund.CustomerRefundCase;
import com.delivery.settlement_service.adapter.JpaRefundQueryAdapter;
import com.delivery.settlement_service.adapter.JpaLedgerAdapter;
import com.delivery.settlement_service.dto.event.OrderCancelledEvent;
import com.delivery.settlement_service.dto.event.DeliveryExceptionReportedEvent;
import com.delivery.settlement_service.dto.response.RefundCaseResponse;
import com.delivery.settlement_service.dto.response.RefundCustomerCaseResponse;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.exception.ResourceNotFoundException;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.delivery.settlement_service.metrics.BusinessMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RefundCaseService {
    private final RefundCaseRepository repository;
    private final RefundOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final boolean providerProcessingEnabled;
    private final BusinessMetrics businessMetrics;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    @Autowired
    public RefundCaseService(RefundCaseRepository repository,
                             RefundOutboxService outboxService,
                             ObjectMapper objectMapper,
                             @Value("${app.refund.provider-processing-enabled:false}") boolean providerProcessingEnabled,
                             BusinessMetrics businessMetrics) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.providerProcessingEnabled = providerProcessingEnabled;
        this.businessMetrics = businessMetrics;
    }

    /** Compatibility constructor for focused fixtures; runtime wiring supplies the shared registry. */
    public RefundCaseService(RefundCaseRepository repository,
                             RefundOutboxService outboxService,
                             ObjectMapper objectMapper,
                             boolean providerProcessingEnabled) {
        this(repository, outboxService, objectMapper, providerProcessingEnabled,
                new BusinessMetrics(new SimpleMeterRegistry()));
    }

    @Transactional
    public RefundCase processOrderCancellation(OrderCancelledEvent event) {
        var adapter = new JpaRefundCaseAdapter(repository, outboxService, dataSourceUrl);
        var core = new DefaultRefundCaseUseCase(adapter, providerProcessingEnabled, UUID::randomUUID);
        var receipt = core.cancel(snapshot(event), () -> fingerprint(event));
        return adapter.requireEntity(receipt.refundId());
    }

    @Transactional
    public RefundCase processDeliveryException(DeliveryExceptionReportedEvent event) {
        var adapter = new JpaRefundCaseAdapter(repository, outboxService, dataSourceUrl);
        var core = new DefaultRefundCaseUseCase(adapter, providerProcessingEnabled, UUID::randomUUID);
        var receipt = core.deliveryException(snapshot(event), () -> fingerprint(event));
        return adapter.requireEntity(receipt.refundId());
    }

    private DefaultRefundQueryUseCase queries() {
        return new DefaultRefundQueryUseCase(new JpaRefundQueryAdapter(repository,businessMetrics),principalOwnershipEnforced);
    }
    @Transactional(readOnly = true)
    public List<RefundCaseResponse> listAdminCases(RefundStatus status,int requestedLimit) {
        return queries().adminCases(JpaLedgerAdapter.enumValue(status,RefundPolicy.Status.class),requestedLimit)
                .stream().map(this::toAdminResponse).toList();
    }
    @Transactional(readOnly = true)
    public RefundCaseResponse getAdminCase(UUID refundId) {
        try {return toAdminResponse(queries().adminCase(refundId));}
        catch (RefundCaseMissing missing) {throw new ResourceNotFoundException("Refund case","refundId",missing.refundId());}
    }
    @Transactional(readOnly = true)
    public List<RefundCustomerCaseResponse> listCustomerCases(Long userId,int requestedLimit) {
        return queries().customerCases(userId,requestedLimit).stream().map(this::toCustomerResponse).toList();
    }
    @Transactional(readOnly = true)
    public List<RefundCustomerCaseResponse> listCustomerCases(Long principalId,Long legacyUserId,int requestedLimit) {
        return queries().customerCases(principalId,legacyUserId,requestedLimit).stream().map(this::toCustomerResponse).toList();
    }

    private RefundCaseResponse toAdminResponse(AdminRefundCase refundCase) {
        return RefundCaseResponse.builder()
                .refundId(refundCase.refundId())
                .eventId(refundCase.eventId())
                .idempotencyKey(refundCase.idempotencyKey())
                .orderId(refundCase.orderId())
                .userId(refundCase.userId())
                .userPrincipalId(refundCase.userPrincipalId())
                .restaurantId(refundCase.restaurantId())
                .previousOrderStatus(refundCase.previousOrderStatus())
                .currentOrderStatus(refundCase.currentOrderStatus())
                .paymentMethod(refundCase.paymentMethod())
                .trigger(refundCase.trigger())
                .component(refundCase.component())
                .status(refundCase.status())
                .currency(refundCase.currency())
                .subtotalAmount(refundCase.subtotalAmount())
                .discountAmount(refundCase.discountAmount())
                .shippingFee(refundCase.shippingFee())
                .totalAmount(refundCase.totalAmount())
                .capturedAmount(refundCase.capturedAmount())
                .refundAmount(refundCase.refundAmount())
                .actorSource(refundCase.actorSource())
                .actorId(refundCase.actorId())
                .reason(refundCase.reason())
                .providerReference(refundCase.providerReference())
                .lastError(refundCase.lastError())
                .attempts(refundCase.attempts())
                .createdAt(refundCase.createdAt())
                .updatedAt(refundCase.updatedAt())
                .processedAt(refundCase.processedAt())
                .build();
    }

    private RefundCustomerCaseResponse toCustomerResponse(CustomerRefundCase refundCase) {
        return RefundCustomerCaseResponse.builder()
                .refundId(refundCase.refundId())
                .orderId(refundCase.orderId())
                .paymentMethod(refundCase.paymentMethod())
                .trigger(refundCase.trigger())
                .status(refundCase.status())
                .currency(refundCase.currency())
                .refundAmount(refundCase.refundAmount())
                .createdAt(refundCase.createdAt())
                .updatedAt(refundCase.updatedAt())
                .processedAt(refundCase.processedAt())
                .build();
    }

    private RefundPolicy.Cancellation snapshot(OrderCancelledEvent event) {
        return event == null ? null : new RefundPolicy.Cancellation(event.getEventId(), event.getEventType(),
                event.getOrderId(), event.getUserId(), event.getUserPrincipalId(), event.getRestaurantId(),
                event.getPreviousStatus(), event.getCurrentStatus(), event.getCancelReason(), event.getCancelledBy(),
                event.getCancelledBySource(), event.getCancelReasonCode(), event.getPaymentMethod(),
                event.getSubtotalPrice(), event.getDiscountAmount(), event.getShippingFee(), event.getTotalPrice());
    }
    private RefundPolicy.DeliveryException snapshot(DeliveryExceptionReportedEvent event) {
        return event == null ? null : new RefundPolicy.DeliveryException(event.getEventId(), event.getEventType(),
                event.getExceptionId(), event.getDeliveryId(), event.getOrderId(), event.getUserId(),
                event.getUserPrincipalId(), event.getRestaurantId(), event.getShipperId(),
                event.getPreviousDeliveryStatus(), event.getCurrentDeliveryStatus(), event.getExceptionStatus(),
                event.getReason(), event.getPaymentMethod(), event.getSubtotalPrice(), event.getDiscountAmount(),
                event.getShippingFee(), event.getTotalPrice());
    }

    private String fingerprint(OrderCancelledEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("refund event cannot be serialized", e);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String fingerprint(DeliveryExceptionReportedEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("delivery exception event cannot be serialized", e);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
