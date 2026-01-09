package com.delivery.livestream_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductPinnedEvent {

    private UUID livestreamId;
    private Long productId;
    private BigDecimal priceAtLive;
    private LocalDateTime pinnedAt;
}
