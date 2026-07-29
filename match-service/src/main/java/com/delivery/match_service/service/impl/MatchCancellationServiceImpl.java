package com.delivery.match_service.service.impl;

import com.delivery.match_service.service.MatchCancellationService;
import com.delivery.match_service.repository.MatchRedisGeoRepository;
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
    private final MatchRedisGeoRepository matchRedisGeoRepository;

    public MatchCancellationServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                        MatchRedisGeoRepository matchRedisGeoRepository) {
        this.redisTemplate = redisTemplate;
        this.matchRedisGeoRepository = matchRedisGeoRepository;
    }

    @Override
    public void markCancelled(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }

        String key = CANCEL_KEY_PREFIX + deliveryId;
        try {
            redisTemplate.opsForValue().set(key, Boolean.TRUE, CANCEL_TTL);
            matchRedisGeoRepository.releaseOfferForDelivery(deliveryId);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot persist matching cancellation", e);
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
            throw new IllegalStateException("Cannot verify matching cancellation", e);
        }
    }
}
