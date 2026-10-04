package com.delivery.settlement.domain.refund;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundOutboxDelivery(UUID eventId,String topic,String eventKey,String payload,int attempts,
        LocalDateTime nextAttemptAt) {}
