package com.delivery.order_service.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionClient {

    private final RestTemplate restTemplate;

    @Value("${promotion.service.url:http://localhost:8096}")
    private String promotionServiceUrl;

    public boolean reserveVouchers(Long userId, Long orderId, List<Long> voucherIds) {
        if (voucherIds == null || voucherIds.isEmpty()) return true;

        try {
            String url = promotionServiceUrl + "/api/promotions/reserve";
            
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("orderId", orderId);
            request.put("voucherIds", voucherIds);

            log.info("🔍 Calling Promotion Service to reserve vouchers: {}", voucherIds);
            
            restTemplate.postForEntity(url, request, String.class);
            log.info("✅ Successfully reserved vouchers for order {}", orderId);
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to reserve vouchers for order {}: {}", orderId, e.getMessage());
            return false;
        }
    }
}
