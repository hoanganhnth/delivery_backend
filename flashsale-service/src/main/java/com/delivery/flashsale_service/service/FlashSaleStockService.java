package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.flashsale.checkout-enabled", havingValue = "true")
public class FlashSaleStockService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final FlashSaleItemRepository itemRepo;

    private static final String STOCK_KEY_PREFIX = "flashsale:stock:";

    // Lua script to atomically check and decrement stock
    private static final String RESERVE_STOCK_SCRIPT =
            "local stockKey = KEYS[1]\n" +
            "local quantity = tonumber(ARGV[1])\n" +
            "local currentStock = tonumber(redis.call('get', stockKey) or '-1')\n" +
            "if currentStock >= quantity then\n" +
            "    redis.call('decrby', stockKey, quantity)\n" +
            "    return 1\n" + // Success
            "else\n" +
            "    return 0\n" + // Not enough stock or not initialized
            "end";

    public void reserveStock(List<ReserveItemRequest> requests) {
        for (ReserveItemRequest req : requests) {
            FlashSaleItem item = itemRepo.findById(req.getFlashSaleItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Flash sale item not found"));

            // 1. Verify Price
            if (item.getFlashSalePrice().compareTo(req.getPrice()) != 0) {
                throw new IllegalArgumentException("Price mismatch for flash sale item");
            }

            // 2. Verify Campaign Status and Time
            FlashSaleCampaign campaign = item.getCampaign();
            if (campaign.getStatus() != FlashSaleCampaign.CampaignStatus.ACTIVE) {
                throw new IllegalArgumentException("Flash sale campaign is not active");
            }

            LocalTime now = LocalTime.now();
            if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
                throw new IllegalArgumentException("Flash sale is outside active hours");
            }

            // 3. Atomically Reserve in Redis
            String key = STOCK_KEY_PREFIX + item.getId();
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(RESERVE_STOCK_SCRIPT);
            redisScript.setResultType(Long.class);

            Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(req.getQuantity()));

            if (result == null || result == 0) {
                throw new IllegalArgumentException("Out of stock for flash sale item " + item.getId());
            }

            // (Optional in real high scale) Send to Kafka to decrement DB asynchronously
            // Here we do it synchronously to keep it simple, or we could let the orchestrator do it.
            // But we already increment soldQuantity in DB. Let's do it simply here.
            item.setSoldQuantity(item.getSoldQuantity() + req.getQuantity());
            itemRepo.save(item);
        }
    }

}
