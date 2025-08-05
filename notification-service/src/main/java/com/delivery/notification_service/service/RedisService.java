package com.delivery.notification_service.service;

import com.delivery.notification_service.common.constants.NotificationConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ✅ Redis Service để manage user sessions và cache theo Backend Instructions
 */
@Slf4j
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Store user session (WebSocket connection)
     */
    public void storeUserSession(Long userId, String sessionId) {
        String key = NotificationConstants.REDIS_USER_SESSIONS + userId;
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        log.debug("📝 Stored session {} for user {}", sessionId, userId);
    }

    /**
     * Remove user session
     */
    public void removeUserSession(Long userId, String sessionId) {
        String key = NotificationConstants.REDIS_USER_SESSIONS + userId;
        redisTemplate.opsForSet().remove(key, sessionId);
        log.debug("🗑️ Removed session {} for user {}", sessionId, userId);
    }

    /**
     * Get all active sessions for user
     */
    public Set<Object> getUserSessions(Long userId) {
        String key = NotificationConstants.REDIS_USER_SESSIONS + userId;
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * Check if user is online (has active sessions)
     */
    public boolean isUserOnline(Long userId) {
        String key = NotificationConstants.REDIS_USER_SESSIONS + userId;
        Long sessionCount = redisTemplate.opsForSet().size(key);
        return sessionCount != null && sessionCount > 0;
    }

    /**
     * Store FCM token for user
     */
    public void storeFcmToken(Long userId, String fcmToken) {
        String key = NotificationConstants.REDIS_FCM_TOKENS + userId;
        redisTemplate.opsForSet().add(key, fcmToken);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
        log.debug("📱 Stored FCM token for user {}", userId);
    }

    /**
     * Remove FCM token for user
     */
    public void removeFcmToken(Long userId, String fcmToken) {
        String key = NotificationConstants.REDIS_FCM_TOKENS + userId;
        redisTemplate.opsForSet().remove(key, fcmToken);
        log.debug("🗑️ Removed FCM token for user {}", userId);
    }

    /**
     * Get all FCM tokens for user
     */
    public Set<Object> getUserFcmTokens(Long userId) {
        String key = NotificationConstants.REDIS_FCM_TOKENS + userId;
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * Cache notification for quick access
     */
    public void cacheNotification(Long notificationId, Object notification) {
        String key = NotificationConstants.REDIS_NOTIFICATION_CACHE + notificationId;
        redisTemplate.opsForValue().set(key, notification, 1, TimeUnit.HOURS);
    }

    /**
     * Get cached notification
     */
    public Object getCachedNotification(Long notificationId) {
        String key = NotificationConstants.REDIS_NOTIFICATION_CACHE + notificationId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Remove cached notification
     */
    public void removeCachedNotification(Long notificationId) {
        String key = NotificationConstants.REDIS_NOTIFICATION_CACHE + notificationId;
        redisTemplate.delete(key);
    }
}
