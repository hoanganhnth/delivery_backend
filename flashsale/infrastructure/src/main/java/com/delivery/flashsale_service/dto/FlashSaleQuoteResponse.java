package com.delivery.flashsale_service.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class FlashSaleQuoteResponse {
    private Long restaurantId;
    private List<Line> items;
    @Data @Builder
    public static class Line {
        private Long flashSaleItemId;
        private Long menuItemId;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
