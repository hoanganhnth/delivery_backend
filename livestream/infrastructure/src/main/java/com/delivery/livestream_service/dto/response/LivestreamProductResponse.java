package com.delivery.livestream_service.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class LivestreamProductResponse {

    private Long id;
    private UUID livestreamId;
    private Long productId;
    private String productName;
    private String productImage;
    private Long restaurantId;
    private String restaurantName;
    private BigDecimal priceAtLive;
    private Boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime pinnedAt;
}
