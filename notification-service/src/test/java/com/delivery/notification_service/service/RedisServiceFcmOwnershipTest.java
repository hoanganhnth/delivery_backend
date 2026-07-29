package com.delivery.notification_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisServiceFcmOwnershipTest {

    @Test
    void rejectsTokenAlreadyOwnedByAnotherAccount() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(0L);

        RedisService service = new RedisService(redisTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> service.storeFcmToken(42L, "device-token"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void ownershipDoesNotExpireBeforeTheUserTokenMembership() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(1L);
        RedisService service = new RedisService(redisTemplate);

        service.storeFcmToken(42L, "device-token");

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(script.capture(), anyList(), eq("42"), eq("device-token"));
        assertThat(script.getValue().getScriptAsString())
                .doesNotContain("'EX'", "EXPIRE")
                .contains("owner ~= ARGV[1]");
    }

    @Test
    void rejectsInvalidIdentityOrBlankTokenBeforeRedisAccess() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisService service = new RedisService(redisTemplate);

        assertThrows(IllegalArgumentException.class, () -> service.storeFcmToken(0L, "device-token"));
        assertThrows(IllegalArgumentException.class, () -> service.storeFcmToken(42L, " "));
        assertThrows(IllegalArgumentException.class, () -> service.getUserFcmTokens(null));

        verifyNoInteractions(redisTemplate);
    }
}
