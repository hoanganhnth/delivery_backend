package com.delivery.restaurant_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Restaurant-owned inventory ledger for one menu item.
 *
 * <p>{@code onHandQuantity} is the remaining saleable quantity and
 * {@code reservedQuantity} is the portion held by non-terminal checkout
 * reservations. The reservation service is the only writer of these counters
 * in the inventory-enabled path.</p>
 */
@Entity
@Table(name = "menu_item_inventory")
@Getter
@Setter
@NoArgsConstructor
public class MenuItemInventory {

    @Id
    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private Long menuItemId;

    @Column(name = "on_hand_quantity", nullable = false)
    private Integer onHandQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(nullable = false)
    private Long revision = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (onHandQuantity == null) onHandQuantity = 0;
        if (reservedQuantity == null) reservedQuantity = 0;
        if (revision == null) revision = 0L;
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int availableQuantity() {
        return Math.max(0, onHandQuantity - reservedQuantity);
    }
}
