package com.delivery.flashsale.application.api;

import com.delivery.flashsale.domain.FlashSaleEventPolicy.Receipt;
import java.util.Optional;
import java.util.UUID;

public interface EventPort {
    int claim(UUID eventId, Receipt receipt);
    Optional<Receipt> find(UUID eventId);
    void commit(UUID reservationId, Long orderId);
    void release(UUID reservationId, Long orderId);
}
