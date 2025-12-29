package com.delivery.match_service.service.impl;

import com.delivery.match_service.service.MatchCancellationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class MatchCancellationServiceImpl implements MatchCancellationService {

    private static final String CANCEL_KEY_PREFIX = "match:cancelled:";
    private static final Duration CANCEL_TTL = Duration.ofHours(2);

    private final RedisTemplate<String, Object> redisTemplate;

    public MatchCancellationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markCancelled(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }

        String key = CANCEL_KEY_PREFIX + deliveryId;
        try {
            redisTemplate.opsForValue().set(key, Boolean.TRUE, CANCEL_TTL);
        } catch (Exception e) {
            log.warn("⚠️ Could not write cancel flag to Redis for deliveryId={}: {}", deliveryId, e.getMessage());
        }
    }

    @Override
    public void clearCancelled(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }

        String key = CANCEL_KEY_PREFIX + deliveryId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("⚠️ Could not clear cancel flag in Redis for deliveryId={}: {}", deliveryId, e.getMessage());
        }
    }

    @Override
    public boolean isCancelled(Long deliveryId) {
        if (deliveryId == null) {
            return false;
        }

        String key = CANCEL_KEY_PREFIX + deliveryId;
        try {
            Object v = redisTemplate.opsForValue().get(key);
            if (v == null) {
                return false;
            }
            if (v instanceof Boolean b) {
                return b;
            }
            return Boolean.parseBoolean(String.valueOf(v));
        } catch (Exception e) {
            // Fail-open: nếu Redis lỗi, vẫn tiếp tục matching (tránh block hệ thống)
            log.warn("⚠️ Could not read cancel flag from Redis for deliveryId={}: {}", deliveryId, e.getMessage());
            return false;
        }
    }
}
