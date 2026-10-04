package com.delivery.settlement_service.service;

import com.delivery.settlement.domain.refund.RefundPolicy;
import com.delivery.settlement_service.adapter.JpaRefundCaseAdapter;
import com.delivery.settlement.application.refund.DefaultRefundCaseUseCase;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RefundCaseService {
    private static final int ADMIN_LIST_LIMIT = 100;

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

    @Transactional(readOnly = true)
    public List<RefundCaseResponse> listAdminCases(RefundStatus status, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), ADMIN_LIST_LIMIT);
        List<RefundCase> cases = status == null
                ? repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                : repository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, limit));
        return cases.stream().map(this::toAdminResponse).toList();
    }

    @Transactional(readOnly = true)
    public RefundCaseResponse getAdminCase(UUID refundId) {
        if (refundId == null) {
            throw new IllegalArgumentException("refundId is required");
        }
        return repository.findById(refundId)
                .map(this::toAdminResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Refund case", "refundId", refundId));
    }

    @Transactional(readOnly = true)
    public List<RefundCustomerCaseResponse> listCustomerCases(Long userId, int requestedLimit) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), ADMIN_LIST_LIMIT);
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::toCustomerResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundCustomerCaseResponse> listCustomerCases(Long principalId, Long legacyUserId, int requestedLimit) {
        if (principalId == null || principalId <= 0 || legacyUserId == null || legacyUserId <= 0) {
            throw new IllegalArgumentException("principalId and legacyUserId are required");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), ADMIN_LIST_LIMIT);
        List<RefundCase> cases = principalOwnershipEnforced
                ? repository.findByUserPrincipalIdOrderByCreatedAtDesc(principalId, PageRequest.of(0, limit))
                : repository.findByPrincipalOrUnmigratedLegacyUserOrderByCreatedAtDesc(
                        principalId, legacyUserId, PageRequest.of(0, limit));
        if (!principalOwnershipEnforced) {
            cases.stream().filter(refundCase -> refundCase.getUserPrincipalId() == null)
                    .forEach(refundCase -> businessMetrics.identityLegacyFallback("customer_refund_list"));
        }
        return cases.stream().map(this::toCustomerResponse).toList();
    }

    private RefundCaseResponse toAdminResponse(RefundCase refundCase) {
        return RefundCaseResponse.builder()
                .refundId(refundCase.getRefundId())
                .eventId(refundCase.getEventId())
                .idempotencyKey(refundCase.getIdempotencyKey())
                .orderId(refundCase.getOrderId())
                .userId(refundCase.getUserId())
                .userPrincipalId(refundCase.getUserPrincipalId())
                .restaurantId(refundCase.getRestaurantId())
                .previousOrderStatus(refundCase.getPreviousOrderStatus())
                .currentOrderStatus(refundCase.getCurrentOrderStatus())
                .paymentMethod(refundCase.getPaymentMethod())
                .trigger(refundCase.getTrigger() == null ? null : refundCase.getTrigger().name())
                .component(refundCase.getComponent() == null ? null : refundCase.getComponent().name())
                .status(refundCase.getStatus() == null ? null : refundCase.getStatus().name())
                .currency(refundCase.getCurrency())
                .subtotalAmount(refundCase.getSubtotalAmount())
                .discountAmount(refundCase.getDiscountAmount())
                .shippingFee(refundCase.getShippingFee())
                .totalAmount(refundCase.getTotalAmount())
                .capturedAmount(refundCase.getCapturedAmount())
                .refundAmount(refundCase.getRefundAmount())
                .actorSource(refundCase.getActorSource())
                .actorId(refundCase.getActorId())
                .reason(refundCase.getReason())
                .providerReference(refundCase.getProviderReference())
                .lastError(refundCase.getLastError())
                .attempts(refundCase.getAttempts())
                .createdAt(refundCase.getCreatedAt())
                .updatedAt(refundCase.getUpdatedAt())
                .processedAt(refundCase.getProcessedAt())
                .build();
    }

    private RefundCustomerCaseResponse toCustomerResponse(RefundCase refundCase) {
        return RefundCustomerCaseResponse.builder()
                .refundId(refundCase.getRefundId())
                .orderId(refundCase.getOrderId())
                .paymentMethod(refundCase.getPaymentMethod())
                .trigger(refundCase.getTrigger() == null ? null : refundCase.getTrigger().name())
                .status(refundCase.getStatus() == null ? null : refundCase.getStatus().name())
                .currency(refundCase.getCurrency())
                .refundAmount(refundCase.getRefundAmount())
                .createdAt(refundCase.getCreatedAt())
                .updatedAt(refundCase.getUpdatedAt())
                .processedAt(refundCase.getProcessedAt())
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
