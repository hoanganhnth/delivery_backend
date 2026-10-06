package com.delivery.promotion.application.api;

import com.delivery.promotion.domain.OrderReservationEventPolicy;
import java.util.UUID;

/** Atomic receipt claim and reservation transition join the host transaction; ACK follows return. */
public interface OrderReservationEventPort {
    boolean claim(PromotionCommands.OrderEvent event);
    OrderReservationEventPolicy.Receipt existing(UUID eventId);
    String commit(UUID reservationId, Long orderId, boolean bulk);
    void release(UUID reservationId, Long orderId, boolean bulk);
    RuntimeException conflict(String message);
}
