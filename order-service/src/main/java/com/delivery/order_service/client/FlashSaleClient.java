package com.delivery.order_service.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleClient {

    private final RestTemplate restTemplate;

    @Value("${flashsale.service.url:http://localhost:8092}")
    private String flashsaleServiceUrl;

    public void reserveStock(List<Map<String, Object>> reserveRequests) {
        if (reserveRequests == null || reserveRequests.isEmpty()) return;

        String url = flashsaleServiceUrl + "/api/flashsales/internal/reserve";
        log.info("🔍 Calling FlashSale Service to reserve stock: {}", reserveRequests);

        restTemplate.postForEntity(url, reserveRequests, String.class);
        log.info("✅ Successfully reserved flash sale stock");
    }
}
