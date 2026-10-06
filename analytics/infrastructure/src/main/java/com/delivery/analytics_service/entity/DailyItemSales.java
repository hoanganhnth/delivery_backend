package com.delivery.analytics_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Additive per-menu-item projection sourced from immutable Order item
 * snapshots. Ordered and cancelled quantities stay separate; no client-side
 * price or mutable menu lookup is used to derive them.
 */
@Entity
@Table(name = "daily_item_sales",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_item_sales_scope",
                columnNames = {"stat_date", "restaurant_id", "menu_item_id"}),
        indexes = {
                @Index(name = "idx_daily_item_sales_date", columnList = "stat_date"),
                @Index(name = "idx_daily_item_sales_restaurant_date",
                        columnList = "restaurant_id,stat_date")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyItemSales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "menu_item_name", nullable = false, length = 255)
    private String menuItemName;

    @Column(name = "ordered_quantity", nullable = false)
    private long orderedQuantity;

    @Column(name = "cancelled_quantity", nullable = false)
    private long cancelledQuantity;

    @Column(name = "ordered_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal orderedRevenue;

    @Column(name = "cancelled_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal cancelledRevenue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
