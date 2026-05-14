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

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
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

    // Lua script to atomically increment stock (release)
    private static final String RELEASE_STOCK_SCRIPT =
            "local stockKey = KEYS[1]\n" +
            "local quantity = tonumber(ARGV[1])\n" +
            "if redis.call('exists', stockKey) == 1 then\n" +
            "    redis.call('incrby', stockKey, quantity)\n" +
            "end\n" +
            "return 1";

    public void reserveStock(List<ReserveItemRequest> requests) {
        for (ReserveItemRequest req : requests) {
            FlashSaleItem item = itemRepo.findById(req.getFlashSaleItemId())
                    .orElseThrow(() -> new RuntimeException("Flash sale item not found"));

            // 1. Verify Price
            if (item.getFlashSalePrice().compareTo(req.getPrice()) != 0) {
                throw new RuntimeException("Price mismatch for flash sale item");
            }

            // 2. Verify Campaign Status and Time
            FlashSaleCampaign campaign = item.getCampaign();
            if (campaign.getStatus() != FlashSaleCampaign.CampaignStatus.ACTIVE) {
                throw new RuntimeException("Flash sale campaign is not active");
            }

            LocalTime now = LocalTime.now();
            if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
                throw new RuntimeException("Flash sale is outside active hours");
            }

            // 3. Atomically Reserve in Redis
            String key = STOCK_KEY_PREFIX + item.getId();
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(RESERVE_STOCK_SCRIPT);
            redisScript.setResultType(Long.class);

            Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(req.getQuantity()));

            if (result == null || result == 0) {
                throw new RuntimeException("Out of stock for flash sale item " + item.getId());
            }

            // (Optional in real high scale) Send to Kafka to decrement DB asynchronously
            // Here we do it synchronously to keep it simple, or we could let the orchestrator do it.
            // But we already increment soldQuantity in DB. Let's do it simply here.
            item.setSoldQuantity(item.getSoldQuantity() + req.getQuantity());
            itemRepo.save(item);
        }
    }

    public void releaseStock(Long flashSaleItemId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + flashSaleItemId;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(RELEASE_STOCK_SCRIPT);
        redisScript.setResultType(Long.class);

        redisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(quantity));

        itemRepo.findById(flashSaleItemId).ifPresent(item -> {
            item.setSoldQuantity(Math.max(0, item.getSoldQuantity() - quantity));
            itemRepo.save(item);
            log.info("Released {} stock for item {} in Redis and DB", quantity, flashSaleItemId);
        });
    }
}
