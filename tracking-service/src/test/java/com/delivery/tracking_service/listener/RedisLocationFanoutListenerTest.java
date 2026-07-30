package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLocationFanoutListenerTest {

    @Test
    void routesEnvelopeToExactDeliveryRoom() {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(("{\"deliveryId\":100,\"location\":{"
                + "\"shipperId\":42,\"latitude\":10.77,\"longitude\":106.7,"
                + "\"isOnline\":true}}").getBytes(StandardCharsets.UTF_8));

        new RedisLocationFanoutListener(new ObjectMapper(), handler).onMessage(message, null);

        verify(handler).broadcastDeliveryLocation(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.argThat(location ->
                        location.getShipperId() == 42L && Boolean.TRUE.equals(location.getIsOnline())));
    }
}
