package com.delivery.analytics_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bản ghi sự kiện thô — lưu trữ mọi event nhận từ Kafka
 * Đây là "source of truth" để Scheduled Job tính toán lại
 */
@Entity
@Table(name = "analytics_events", indexes = {
    @Index(name = "idx_event_type_time", columnList = "event_type, event_time"),
    @Index(name = "idx_restaurant_id", columnList = "restaurant_id"),
    @Index(name = "idx_event_time", columnList = "event_time")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "shipper_id")
    private Long shipperId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_status", length = 30)
    private String orderStatus;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "restaurant_name")
    private String restaurantName;

    /** Raw JSON payload cho debugging/re-processing */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
