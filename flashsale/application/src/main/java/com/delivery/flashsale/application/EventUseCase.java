package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.EventPort;
import com.delivery.flashsale.domain.FlashSaleEventPolicy;
import com.delivery.flashsale.domain.FlashSaleEventPolicy.Receipt;
import java.util.UUID;

public final class EventUseCase {
    private final EventPort port;
    public EventUseCase(EventPort port) { this.port = port; }
    public void process(UUID eventId, Receipt receipt) {
        if (port.claim(eventId, receipt) == 0) {
            Receipt existing = port.find(eventId).orElseThrow(() -> new IllegalStateException(
                    "flash-sale receipt conflict resolved without a committed receipt"));
            FlashSaleEventPolicy.requireExactReplay(existing, receipt); return;
        }
        if (receipt.reservationId() == null) return;
        if ("COMMIT".equals(receipt.action())) port.commit(receipt.reservationId(), receipt.orderId());
        else port.release(receipt.reservationId(), receipt.orderId());
    }
}
