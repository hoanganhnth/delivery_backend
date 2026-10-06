package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.OutboxPort;
import com.delivery.flashsale.domain.FlashSaleEventPolicy;
import java.util.UUID;

public final class OutboxUseCase<R> {
    private final OutboxPort<R> port;
    public OutboxUseCase(OutboxPort<R> port) { this.port = port; }
    public UUID enqueue(R reservation) {
        String type = FlashSaleEventPolicy.eventType(port.state(reservation));
        UUID id = FlashSaleEventPolicy.eventId(port.reservationId(reservation), type);
        if (!port.exists(id)) port.save(reservation, id, type, port.now());
        return id;
    }
}
