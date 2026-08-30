package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.dto.event.DeliveryExceptionReportedEvent;
import com.delivery.delivery_service.dto.response.DeliveryExceptionResponse;
import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryException;
import com.delivery.delivery_service.entity.DeliveryExceptionStatus;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.exception.AccessDeniedException;
import com.delivery.delivery_service.exception.InvalidStatusException;
import com.delivery.delivery_service.exception.ResourceNotFoundException;
import com.delivery.delivery_service.repository.DeliveryExceptionRepository;
import com.delivery.delivery_service.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit post-pickup failure workflow. RETURNING/RETURNED are only persisted
 * through this service and never sent through delivery.status-updated.
 */
@Slf4j
@Service
public class DeliveryExceptionService {

    static final int RETRY_WINDOW_MINUTES = 15;
    private static final int SWEEP_LIMIT = 100;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryExceptionRepository exceptionRepository;
    private final DeliveryEventPublisher eventPublisher;
    private final DeliveryBatchProgressService batchProgressService;
    private final ShipperIdentityResolver shipperIdentityResolver;

    @Value("${delivery.exception.enabled:false}")
    private boolean exceptionEnabled;

    public DeliveryExceptionService(DeliveryRepository deliveryRepository,
                                    DeliveryExceptionRepository exceptionRepository,
                                    DeliveryEventPublisher eventPublisher,
                                    DeliveryBatchProgressService batchProgressService,
                                    ShipperIdentityResolver shipperIdentityResolver) {
        this.deliveryRepository = deliveryRepository;
        this.exceptionRepository = exceptionRepository;
        this.eventPublisher = eventPublisher;
        this.batchProgressService = batchProgressService;
        this.shipperIdentityResolver = shipperIdentityResolver;
    }

    @Transactional
    public DeliveryExceptionResponse reportFailure(Long deliveryId,
                                                    String reason,
                                                    Long principalId,
                                                    Long legacyUserId,
                                                    String role) {
        requireEnabled();
        String normalizedReason = requireReason(reason);
        Delivery delivery = findDeliveryForUpdate(deliveryId);
        Long shipperId = requireAssignedShipper(delivery, principalId, legacyUserId, role);
        DeliveryException existing = exceptionRepository.findByDeliveryIdForUpdate(deliveryId).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getReason(), normalizedReason)) {
                throw new InvalidStatusException("Sự cố giao hàng đã tồn tại với lý do khác");
            }
            return switch (existing.getStatus()) {
                case RETRY_AVAILABLE -> existing.getRetryDeadlineAt() != null
                        && !existing.getRetryDeadlineAt().isAfter(LocalDateTime.now())
                        ? toResponse(beginReturn(delivery, existing))
                        : toResponse(existing);
                case RETRY_USED -> toResponse(beginReturn(delivery, existing));
                case RETURNING, RETURNED -> toResponse(existing);
                case RESOLVED -> throw new InvalidStatusException("Đơn đã được giao thành công sau lần retry");
            };
        }
        requirePostPickup(delivery);

        LocalDateTime now = LocalDateTime.now();
        DeliveryException exceptionCase = new DeliveryException();
        exceptionCase.setExceptionId(UUID.randomUUID());
        exceptionCase.setDeliveryId(delivery.getId());
        exceptionCase.setOrderId(delivery.getOrderId());
        exceptionCase.setShipperId(shipperId);
        exceptionCase.setCustomerId(delivery.getCreatorId());
        exceptionCase.setCustomerPrincipalId(delivery.getCustomerPrincipalId());
        exceptionCase.setRestaurantId(delivery.getRestaurantId());
        exceptionCase.setReason(normalizedReason);
        exceptionCase.setStatus(DeliveryExceptionStatus.RETRY_AVAILABLE);
        exceptionCase.setReportedAt(now);
        exceptionCase.setRetryDeadlineAt(now.plusMinutes(RETRY_WINDOW_MINUTES));
        exceptionRepository.save(exceptionCase);
        eventPublisher.publishDeliveryExceptionReported(toReportedEvent(delivery, exceptionCase));
        return toResponse(exceptionCase);
    }

    @Transactional
    public DeliveryExceptionResponse useRetry(Long deliveryId,
                                               Long principalId,
                                               Long legacyUserId,
                                               String role) {
        requireEnabled();
        Delivery delivery = findDeliveryForUpdate(deliveryId);
        requireAssignedShipper(delivery, principalId, legacyUserId, role);
        DeliveryException exceptionCase = exceptionRepository.findByDeliveryIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố giao hàng"));
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RETRY_USED) return toResponse(exceptionCase);
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNING
                || exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNED) {
            throw new InvalidStatusException("Đơn hàng đang hoặc đã được hoàn về nhà hàng");
        }
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RESOLVED) {
            throw new InvalidStatusException("Đơn đã được giao thành công sau lần retry");
        }
        if (!exceptionCase.getRetryDeadlineAt().isAfter(LocalDateTime.now())) {
            return toResponse(beginReturn(delivery, exceptionCase));
        }
        requirePostPickup(delivery);
        exceptionCase.setStatus(DeliveryExceptionStatus.RETRY_USED);
        exceptionCase.setRetryUsedAt(LocalDateTime.now());
        exceptionRepository.save(exceptionCase);
        publishExceptionUpdate(delivery, exceptionCase);
        return toResponse(exceptionCase);
    }

    @Transactional
    public DeliveryExceptionResponse confirmReturn(Long deliveryId,
                                                    Long principalId,
                                                    Long legacyUserId,
                                                    String role) {
        requireEnabled();
        Delivery delivery = findDeliveryForUpdate(deliveryId);
        requireRestaurantOwner(delivery, principalId, legacyUserId, role);
        DeliveryException exceptionCase = exceptionRepository.findByDeliveryIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố giao hàng"));
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNED) return toResponse(exceptionCase);
        if (exceptionCase.getStatus() != DeliveryExceptionStatus.RETURNING
                || delivery.getStatus() != DeliveryStatus.RETURNING) {
            throw new InvalidStatusException("Đơn hàng chưa ở trạng thái chờ xác nhận hoàn trả");
        }

        LocalDateTime now = LocalDateTime.now();
        exceptionCase.setStatus(DeliveryExceptionStatus.RETURNED);
        exceptionCase.setReturnedAt(now);
        exceptionCase.setReturnedByPrincipalId(principalId);
        delivery.setStatus(DeliveryStatus.RETURNED);
        delivery.setUpdatedAt(now);
        deliveryRepository.save(delivery);
        exceptionRepository.save(exceptionCase);
        publishExceptionUpdate(delivery, exceptionCase);

        boolean routeTerminal = batchProgressService == null
                || batchProgressService.applyExceptionReturn(delivery, true);
        if (routeTerminal && delivery.getShipperId() != null) {
            if (delivery.getBatchId() == null) {
                publishShipperStatusChange(
                        delivery.getShipperId(), "AVAILABLE", delivery.getId(), delivery.getOrderId(),
                        null, delivery.getSimulationContext());
            } else {
                publishShipperStatusChange(
                        delivery.getShipperId(), "AVAILABLE", delivery.getId(), delivery.getOrderId(), delivery.getBatchId(),
                        delivery.getSimulationContext());
            }
        }
        return toResponse(exceptionCase);
    }

    @Transactional(readOnly = true)
    public DeliveryExceptionResponse getException(Long deliveryId,
                                                  Long principalId,
                                                  Long legacyUserId,
                                                  String role) {
        requireEnabled();
        if (deliveryId == null || deliveryId <= 0) throw new InvalidStatusException("Delivery ID is required");
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin giao hàng với ID: " + deliveryId));
        requireViewer(delivery, principalId, legacyUserId, role);
        return exceptionRepository.findByDeliveryId(deliveryId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố giao hàng"));
    }

    /** Scheduler entry point; each expired unused retry becomes a return. */
    @Transactional
    public int expireRetryWindows() {
        if (!exceptionEnabled) return 0;
        LocalDateTime now = LocalDateTime.now();
        List<DeliveryException> candidates = exceptionRepository.findRetryDeadlineExpiredForUpdate(
                DeliveryExceptionStatus.RETRY_AVAILABLE, now, PageRequest.of(0, SWEEP_LIMIT));
        int transitioned = 0;
        for (DeliveryException candidate : candidates) {
            try {
                // Keep the lock order identical to reportFailure/useRetry:
                // Delivery first, then its exception row. The initial scan is
                // deliberately unlocked so a concurrent shipper command cannot
                // deadlock the expiry worker.
                Delivery delivery = deliveryRepository.findByIdForUpdate(candidate.getDeliveryId())
                        .orElseThrow(() -> new IllegalStateException("Exception delivery is missing"));
                DeliveryException exceptionCase = exceptionRepository.findByIdForUpdate(candidate.getExceptionId())
                        .orElseThrow(() -> new IllegalStateException("Delivery exception disappeared"));
                if (exceptionCase.getStatus() != DeliveryExceptionStatus.RETRY_AVAILABLE
                        || exceptionCase.getRetryDeadlineAt() == null
                        || exceptionCase.getRetryDeadlineAt().isAfter(now)) {
                    continue;
                }
                if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
                    exceptionCase.setStatus(DeliveryExceptionStatus.RESOLVED);
                    exceptionRepository.save(exceptionCase);
                    continue;
                }
                if (beginReturn(delivery, exceptionCase).getStatus() == DeliveryExceptionStatus.RETURNING) {
                    transitioned++;
                }
            } catch (RuntimeException failure) {
                // A malformed historic row must remain visible/retryable; do not
                // mark it returned based on an incomplete delivery snapshot.
                log.warn("Unable to expire retry window for delivery exception {}", candidate.getExceptionId(), failure);
            }
        }
        return transitioned;
    }

    /** Called from the normal DELIVERED transaction so an old retry timer cannot return a delivered order. */
    @Transactional
    public void markResolvedAfterSuccessfulDelivery(Delivery delivery) {
        if (!exceptionEnabled || delivery == null || delivery.getId() == null) return;
        DeliveryException exceptionCase = exceptionRepository.findByDeliveryIdForUpdate(delivery.getId()).orElse(null);
        if (exceptionCase == null) return;
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RETRY_AVAILABLE
                || exceptionCase.getStatus() == DeliveryExceptionStatus.RETRY_USED) {
            exceptionCase.setStatus(DeliveryExceptionStatus.RESOLVED);
            exceptionRepository.save(exceptionCase);
            publishExceptionUpdate(delivery, exceptionCase);
            return;
        }
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNING
                || exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNED) {
            throw new InvalidStatusException("Không thể hoàn tất đơn đang trong luồng hoàn trả");
        }
    }

    private DeliveryException beginReturn(Delivery delivery, DeliveryException exceptionCase) {
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNING
                || exceptionCase.getStatus() == DeliveryExceptionStatus.RETURNED) {
            return exceptionCase;
        }
        if (exceptionCase.getStatus() == DeliveryExceptionStatus.RESOLVED) {
            throw new InvalidStatusException("Đơn đã được giao thành công sau lần retry");
        }
        requirePostPickup(delivery);
        LocalDateTime now = LocalDateTime.now();
        exceptionCase.setStatus(DeliveryExceptionStatus.RETURNING);
        exceptionCase.setReturningAt(now);
        delivery.setStatus(DeliveryStatus.RETURNING);
        delivery.setUpdatedAt(now);
        deliveryRepository.save(delivery);
        exceptionRepository.save(exceptionCase);
        publishExceptionUpdate(delivery, exceptionCase);
        if (batchProgressService != null) {
            batchProgressService.applyExceptionReturn(delivery, false);
        }
        return exceptionCase;
    }

    private Delivery findDeliveryForUpdate(Long deliveryId) {
        if (deliveryId == null || deliveryId <= 0) throw new InvalidStatusException("Delivery ID is required");
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin giao hàng với ID: " + deliveryId));
    }

    private Long requireAssignedShipper(Delivery delivery, Long principalId, Long legacyUserId, String role) {
        Long shipperId = shipperIdentityResolver.resolveShipperId(principalId, legacyUserId, role);
        if (!Objects.equals(shipperId, delivery.getShipperId())) {
            throw new AccessDeniedException("Chỉ shipper được phân công mới có thể báo sự cố giao hàng");
        }
        return shipperId;
    }

    private void requireRestaurantOwner(Delivery delivery, Long principalId, Long legacyUserId, String role) {
        if (!RoleConstants.RESTAURANT_OWNER.equals(role)
                || !((delivery.getRestaurantOwnerPrincipalId() != null
                        && delivery.getRestaurantOwnerPrincipalId().equals(principalId))
                    || (delivery.getRestaurantOwnerPrincipalId() == null
                        && delivery.getRestaurantOwnerId() != null
                        && delivery.getRestaurantOwnerId().equals(legacyUserId)))) {
            throw new AccessDeniedException("Chỉ chủ nhà hàng của đơn mới có thể xác nhận hoàn trả");
        }
    }

    private void requireViewer(Delivery delivery, Long principalId, Long legacyUserId, String role) {
        if (RoleConstants.ADMIN.equals(role)) return;
        if (RoleConstants.SHIPPER.equals(role)) {
            requireAssignedShipper(delivery, principalId, legacyUserId, role);
            return;
        }
        if (RoleConstants.USER.equals(role)
                && ((delivery.getCustomerPrincipalId() != null && delivery.getCustomerPrincipalId().equals(principalId))
                    || (delivery.getCustomerPrincipalId() == null && delivery.getCreatorId().equals(legacyUserId)))) {
            return;
        }
        if (RoleConstants.RESTAURANT_OWNER.equals(role)
                && ((delivery.getRestaurantOwnerPrincipalId() != null
                        && delivery.getRestaurantOwnerPrincipalId().equals(principalId))
                    || (delivery.getRestaurantOwnerPrincipalId() == null
                        && delivery.getRestaurantOwnerId() != null
                        && delivery.getRestaurantOwnerId().equals(legacyUserId)))) {
            return;
        }
        throw new AccessDeniedException("Bạn không có quyền xem sự cố giao hàng");
    }

    private void requirePostPickup(Delivery delivery) {
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP && delivery.getStatus() != DeliveryStatus.DELIVERING) {
            throw new InvalidStatusException("Chỉ có thể báo sự cố sau khi đã lấy hàng");
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.trim().length() > 500) {
            throw new InvalidStatusException("Lý do sự cố là bắt buộc và không quá 500 ký tự");
        }
        return reason.trim();
    }

    private void requireEnabled() {
        if (!exceptionEnabled) throw new InvalidStatusException("Luồng sự cố giao hàng chưa được bật");
    }

    private DeliveryExceptionReportedEvent toReportedEvent(Delivery delivery, DeliveryException exceptionCase) {
        BigDecimal subtotal = delivery.getSubtotalPrice();
        // The exception contract uses the gross shipping fee and the complete
        // discount delta. customerShippingFee is already net of freeship and
        // itemDiscount already includes shop-funded item discounts, so using
        // either together with their component discounts double-counts them.
        BigDecimal shipping = delivery.getGrossShippingFee() == null
                ? delivery.getShippingFee() : delivery.getGrossShippingFee();
        BigDecimal total = delivery.getTotalPrice();
        BigDecimal discount = subtotal == null || shipping == null || total == null
                ? null : subtotal.add(shipping).subtract(total);
        if (subtotal == null || shipping == null || total == null
                || subtotal.signum() < 0 || shipping.signum() < 0 || total.signum() <= 0
                || discount.signum() < 0) {
            throw new InvalidStatusException("Sự cố giao hàng cần snapshot tiền tệ bất biến hợp lệ");
        }
        UUID eventId = UUID.nameUUIDFromBytes(("delivery-exception-reported:" + exceptionCase.getExceptionId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return DeliveryExceptionReportedEvent.builder()
                .eventId(eventId)
                .eventType("DELIVERY_EXCEPTION_REPORTED")
                .occurredAt(exceptionCase.getReportedAt())
                .exceptionId(exceptionCase.getExceptionId())
                .deliveryId(delivery.getId())
                .orderId(delivery.getOrderId())
                .userId(delivery.getCreatorId())
                .userPrincipalId(delivery.getCustomerPrincipalId())
                .restaurantId(delivery.getRestaurantId())
                .shipperId(delivery.getShipperId())
                .previousDeliveryStatus(delivery.getStatus().name())
                .currentDeliveryStatus(delivery.getStatus().name())
                .exceptionStatus(exceptionCase.getStatus().name())
                .reason(exceptionCase.getReason())
                .paymentMethod(delivery.getPaymentMethod())
                .subtotalPrice(subtotal)
                .discountAmount(discount)
                .shippingFee(shipping)
                .totalPrice(total)
                .build();
    }

    private void publishExceptionUpdate(Delivery delivery, DeliveryException exceptionCase) {
        DeliveryExceptionReportedEvent event = toReportedEvent(delivery, exceptionCase);
        event.setEventType("DELIVERY_EXCEPTION_UPDATED");
        event.setEventId(UUID.nameUUIDFromBytes(("delivery-exception-updated:"
                + exceptionCase.getExceptionId() + ":" + exceptionCase.getStatus())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        eventPublisher.publishDeliveryExceptionUpdated(event);
    }

    private void publishShipperStatusChange(Long shipperId, String status, Long deliveryId, Long orderId,
                                            UUID batchId, com.delivery.identity.contracts.SimulationContext context) {
        com.delivery.identity.contracts.SimulationContext normalized =
                com.delivery.identity.contracts.SimulationContext.orReal(context);
        normalized.requireValid();
        if (normalized.isSimulation()) {
            eventPublisher.publishShipperStatusChange(shipperId, status, deliveryId, orderId, batchId, normalized);
        } else if (batchId == null) {
            eventPublisher.publishShipperStatusChange(shipperId, status, deliveryId, orderId);
        } else {
            eventPublisher.publishShipperStatusChange(shipperId, status, deliveryId, orderId, batchId);
        }
    }

    private DeliveryExceptionResponse toResponse(DeliveryException exceptionCase) {
        DeliveryExceptionResponse response = new DeliveryExceptionResponse();
        response.setExceptionId(exceptionCase.getExceptionId());
        response.setDeliveryId(exceptionCase.getDeliveryId());
        response.setStatus(exceptionCase.getStatus());
        response.setReason(exceptionCase.getReason());
        response.setReportedAt(exceptionCase.getReportedAt());
        response.setRetryDeadlineAt(exceptionCase.getRetryDeadlineAt());
        response.setRetryUsedAt(exceptionCase.getRetryUsedAt());
        response.setReturningAt(exceptionCase.getReturningAt());
        response.setReturnedAt(exceptionCase.getReturnedAt());
        return response;
    }
}
