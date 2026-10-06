package com.delivery.flashsale_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "flash_sale_reservation_lines",
        uniqueConstraints = @UniqueConstraint(name = "uk_flash_reservation_line_item",
                columnNames = {"reservation_id", "flash_sale_item_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FlashSaleReservationLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private FlashSaleReservation reservation;
    @Column(name = "flash_sale_item_id", nullable = false, updatable = false) private Long flashSaleItemId;
    @Column(name = "menu_item_id", nullable = false, updatable = false) private Long menuItemId;
    @Column(nullable = false, updatable = false) private Integer quantity;
    @Column(name = "unit_price", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal unitPrice;
}
