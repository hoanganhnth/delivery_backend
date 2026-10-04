package com.delivery.settlement.domain.cod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CodHoldCommand(UUID eventId, Long shipperId, UUID matchingSessionId, UUID waveId, List<Item> offers) {
    public CodHoldCommand {
        if (offers != null) offers = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(offers));
    }
    public record Item(UUID holdId, UUID offerId, Long orderId, Long deliveryId, BigDecimal amount, LocalDateTime expiresAt) {}
    public void validate() {
        if (eventId == null || shipperId == null || shipperId <= 0 || matchingSessionId == null
                || offers == null || offers.isEmpty() || offers.size() > 3) {
            throw new IllegalArgumentException("Invalid COD capacity hold request");
        }
        for (Item item : offers) {
            if (item.offerId() == null || item.orderId() == null || item.deliveryId() == null
                    || item.amount() == null || item.amount().signum() <= 0 || item.expiresAt() == null) {
                throw new IllegalArgumentException("Invalid COD capacity hold item");
            }
        }
    }
    public String idempotencyKey(Item item) { return matchingSessionId + ":" + waveId + ":" + item.offerId(); }
}
