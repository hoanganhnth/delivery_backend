package com.delivery.notification_service.service;

import com.delivery.notification_service.common.constants.NotificationConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * ✅ Redis Service để manage user sessions và cache theo Backend Instructions
 */
@Slf4j
@Service
public class RedisService {

    private static final DefaultRedisScript<Long> REGISTER_FCM_TOKEN = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[1])
            if owner and owner ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_FCM_TOKEN = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[1])
            if not owner or owner ~= ARGV[1] then
              return 0
            end
            redis.call('SREM', KEYS[2], ARGV[2])
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Store FCM token for user
     */
    public void storeFcmToken(Long userId, String fcmToken) {
        requirePositiveId(userId, "userId");
        requireNonBlank(fcmToken, "fcmToken");
        String userKey = NotificationConstants.REDIS_FCM_TOKENS + userId;
        String ownerKey = NotificationConstants.REDIS_FCM_TOKEN_OWNER + hashToken(fcmToken);
        Long registered = redisTemplate.execute(
                REGISTER_FCM_TOKEN,
                List.of(ownerKey, userKey),
                userId.toString(), fcmToken);
        if (registered == null || registered != 1L) {
            throw new IllegalArgumentException("FCM token is already registered to another account");
        }
        log.debug("📱 Stored FCM token for user {}", userId);
    }

    /**
     * Remove FCM token for user
     */
    public void removeFcmToken(Long userId, String fcmToken) {
        requirePositiveId(userId, "userId");
        requireNonBlank(fcmToken, "fcmToken");
        String userKey = NotificationConstants.REDIS_FCM_TOKENS + userId;
        String ownerKey = NotificationConstants.REDIS_FCM_TOKEN_OWNER + hashToken(fcmToken);
        redisTemplate.execute(
                REMOVE_FCM_TOKEN,
                List.of(ownerKey, userKey),
                userId.toString(), fcmToken);
        log.debug("🗑️ Removed FCM token for user {}", userId);
    }

    /**
     * Get all FCM tokens for user
     */
    public Set<Object> getUserFcmTokens(Long userId) {
        requirePositiveId(userId, "userId");
        String key = NotificationConstants.REDIS_FCM_TOKENS + userId;
        return redisTemplate.opsForSet().members(key);
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
