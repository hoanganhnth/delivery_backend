package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.PromotionOutboxEvent;
import com.delivery.promotion_service.entity.VoucherReservation;
import com.delivery.promotion_service.repository.PromotionOutboxEventRepository;
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
public class PromotionOutboxService {
    private final PromotionOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String reservationTopic;

    public PromotionOutboxService(PromotionOutboxEventRepository repository,
                                  ObjectMapper objectMapper,
                                  @Value("${app.kafka.topics.voucher-reservation-events:"
                                          + "voucher.reservation.events}") String reservationTopic) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.reservationTopic = reservationTopic;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(VoucherReservation reservation) {
        String eventType = "VOUCHER_RESERVATION_" + reservation.getState().name();
        UUID eventId = UUID.nameUUIDFromBytes((reservation.getReservationId() + ":" + eventType)
                .getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) return eventId;

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("occurredAt", now);
        payload.put("reservationId", reservation.getReservationId());
        payload.put("orderId", reservation.getOrderId());
        payload.put("userId", reservation.getUserId());
        payload.put("voucherId", reservation.getVoucherId());
        payload.put("restaurantId", reservation.getRestaurantId());
        payload.put("discountAmount", reservation.getDiscountAmount());
        payload.put("state", reservation.getState());
        payload.put("expiresAt", reservation.getExpiresAt());

        PromotionOutboxEvent event = new PromotionOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("VOUCHER_RESERVATION");
        event.setAggregateId(reservation.getReservationId().toString());
        event.setEventType(eventType);
        event.setTopic(reservationTopic);
        event.setEventKey(reservation.getOrderId().toString());
        event.setPayload(toJson(payload));
        event.setStatus(PromotionOutboxEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        repository.save(event);
        return eventId;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Voucher reservation event is not serializable", exception);
        }
    }
}
