package com.delivery.search_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public void put(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, DEFAULT_TTL);
            log.debug("Cached search results for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache search results for key: {}", key, e);
        }
    }

    public Optional<Object> get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("Cache hit for key: {}", key);
                return Optional.of(value);
            }
        } catch (Exception e) {
            log.error("Failed to get cache for key: {}", key, e);
        }
        return Optional.empty();
    }

    public void evictByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} cache keys with prefix: {}", keys.size(), prefix);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache keys with prefix: {}", prefix, e);
        }
    }
}
