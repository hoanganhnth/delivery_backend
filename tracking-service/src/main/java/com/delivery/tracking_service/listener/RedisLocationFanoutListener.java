package com.delivery.tracking_service.listener;

import com.delivery.tracking_service.dto.event.LocationFanoutEnvelope;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RedisLocationFanoutListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ShipperLocationWebSocketHandler handler;

    public RedisLocationFanoutListener(ObjectMapper objectMapper,
                                       ShipperLocationWebSocketHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            LocationFanoutEnvelope envelope = objectMapper.readValue(
                    message.getBody(), LocationFanoutEnvelope.class);
            handler.broadcastDeliveryLocation(envelope.deliveryId(), envelope.location());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot dispatch Redis location fanout", exception);
        }
    }
}
