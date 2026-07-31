package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FlashSaleOutboxService {
    private final FlashSaleOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String topic;

    public FlashSaleOutboxService(FlashSaleOutboxEventRepository repository, ObjectMapper objectMapper,
            @Value("${app.kafka.topics.flash-sale-reservation-events:flash-sale.reservation.events}") String topic) {
        this.repository = repository; this.objectMapper = objectMapper; this.topic = topic;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(FlashSaleReservation reservation) {
        String eventType = "FLASH_SALE_RESERVATION_" + reservation.getState().name();
        UUID eventId = UUID.nameUUIDFromBytes((reservation.getReservationId() + ":" + eventType)
                .getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) return eventId;
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId); payload.put("eventType", eventType); payload.put("occurredAt", now);
        payload.put("reservationId", reservation.getReservationId()); payload.put("orderId", reservation.getOrderId());
        payload.put("userId", reservation.getUserId()); payload.put("restaurantId", reservation.getRestaurantId());
        payload.put("state", reservation.getState()); payload.put("expiresAt", reservation.getExpiresAt());
        payload.put("items", reservation.getLines().stream().map(line -> Map.of(
                "flashSaleItemId", line.getFlashSaleItemId(), "menuItemId", line.getMenuItemId(),
                "quantity", line.getQuantity(), "unitPrice", line.getUnitPrice())).toList());

        FlashSaleOutboxEvent event = new FlashSaleOutboxEvent();
        event.setEventId(eventId); event.setAggregateType("FLASH_SALE_RESERVATION");
        event.setAggregateId(reservation.getReservationId().toString()); event.setEventType(eventType);
        event.setTopic(topic); event.setEventKey(reservation.getOrderId().toString()); event.setPayload(json(payload));
        event.setStatus(FlashSaleOutboxEvent.Status.PENDING); event.setAttempts(0);
        event.setNextAttemptAt(now); event.setCreatedAt(now); repository.save(event); return eventId;
    }

    private String json(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Flash-sale event is not serializable", e); }
    }
}
