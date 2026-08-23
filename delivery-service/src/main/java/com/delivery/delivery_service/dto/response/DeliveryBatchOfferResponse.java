package com.delivery.delivery_service.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class DeliveryBatchOfferResponse {
    private UUID batchId;
    private LocalDateTime expiresAt;
    private List<DeliveryOfferResponse> offers;
}
