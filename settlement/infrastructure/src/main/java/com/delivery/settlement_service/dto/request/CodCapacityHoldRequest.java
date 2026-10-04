package com.delivery.settlement_service.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CodCapacityHoldRequest {
    private UUID eventId;
    private Long shipperId;
    private UUID matchingSessionId;
    private UUID waveId;
    private List<Item> offers;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private UUID holdId;
        private UUID offerId;
        private Long orderId;
        private Long deliveryId;
        private BigDecimal amount;
        private LocalDateTime expiresAt;
    }
}
