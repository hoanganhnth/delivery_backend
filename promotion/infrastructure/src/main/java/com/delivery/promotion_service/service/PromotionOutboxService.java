package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.PromotionOutboxEvent;
import com.delivery.promotion_service.entity.PromotionReservation;
import com.delivery.promotion_service.entity.PromotionReservationLine;
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
import java.util.List;
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
        payload.put("userPrincipalId", reservation.getUserPrincipalId());
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

    /**
     * Writes the additive multi-layer reservation event in the same transaction
     * as the reservation mutation.  The line snapshot is deliberately embedded
     * in the outbox payload so downstream consumers never need to re-read a
     * mutable voucher row to reconstruct funding attribution.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(PromotionReservation reservation, List<PromotionReservationLine> lines) {
        String eventType = "PROMOTION_RESERVATION_" + reservation.getState().name();
        UUID eventId = UUID.nameUUIDFromBytes((reservation.getReservationId() + ":" + eventType)
                .getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) return eventId;

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 2);
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("occurredAt", now);
        payload.put("reservationId", reservation.getReservationId());
        payload.put("orderId", reservation.getOrderId());
        payload.put("userId", reservation.getUserId());
        payload.put("userPrincipalId", reservation.getUserPrincipalId());
        payload.put("restaurantId", reservation.getRestaurantId());
        payload.put("subtotal", reservation.getSubtotal());
        payload.put("grossShippingFee", reservation.getGrossShippingFee());
        payload.put("itemDiscount", reservation.getItemDiscount());
        payload.put("shippingDiscount", reservation.getShippingDiscount());
        payload.put("totalDiscount", reservation.getTotalDiscount());
        payload.put("customerShippingFee", reservation.getCustomerShippingFee());
        payload.put("state", reservation.getState());
        payload.put("expiresAt", reservation.getExpiresAt());
        payload.put("lines", lines == null ? List.of() : lines.stream().map(line -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("voucherId", line.getVoucherId());
            value.put("voucherCode", line.getVoucherCode());
            value.put("layer", line.getLayer());
            value.put("fundingSource", line.getFundingSource());
            value.put("discountBase", line.getDiscountBase());
            value.put("discountAmount", line.getDiscountAmount());
            value.put("state", line.getState());
            return value;
        }).toList());

        PromotionOutboxEvent event = new PromotionOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("PROMOTION_RESERVATION");
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
