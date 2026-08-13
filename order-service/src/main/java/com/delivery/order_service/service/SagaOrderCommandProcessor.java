package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.DeliveryStatusUpdatedEvent;
import com.delivery.order_service.dto.event.ShipperEvent;
import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Applies one validated Saga command in the same transaction as its inbox
 * receipt. The Kafka listener acknowledges only after this method returns.
 */
@Service
public class SagaOrderCommandProcessor {

    private final SagaCommandReceiptService receipts;
    private final OrderEventService orderEventService;
    private final OrderService orderService;

    public SagaOrderCommandProcessor(SagaCommandReceiptService receipts,
                                     OrderEventService orderEventService,
                                     OrderService orderService) {
        this.receipts = receipts;
        this.orderEventService = orderEventService;
        this.orderService = orderService;
    }

    @Transactional
    public boolean applyDeliveryStatus(UUID eventId, Long orderId, String sagaStatus,
                                       String rawPayload, DeliveryStatusUpdatedEvent event) {
        if (!claim(eventId, orderId, sagaStatus, rawPayload)) {
            return false;
        }
        orderEventService.handleDeliveryStatusUpdate(event);
        return true;
    }

    @Transactional
    public boolean applyShipperAccepted(UUID eventId, Long orderId, String sagaStatus,
                                        String rawPayload, ShipperEvent event) {
        if (!claim(eventId, orderId, sagaStatus, rawPayload)) {
            return false;
        }
        orderEventService.handleShipperAccepted(event);
        return true;
    }

    @Transactional
    public boolean applyShipperNotFound(UUID eventId, Long orderId, String sagaStatus,
                                        String rawPayload, ShipperNotFoundEvent event) {
        if (!claim(eventId, orderId, sagaStatus, rawPayload)) {
            return false;
        }
        orderService.updateOrderStatusFromShipperNotFoundEvent(event);
        return true;
    }

    private boolean claim(UUID eventId, Long orderId, String sagaStatus, String rawPayload) {
        return receipts.claim(eventId, SagaCommandReceiptService.UPDATE_ORDER_STATUS,
                orderId, sagaStatus, rawPayload);
    }
}
