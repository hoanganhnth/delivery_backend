package com.delivery.restaurant.infrastructure.inventory;

import com.delivery.restaurant.application.api.InventoryReceiptPort;
import com.delivery.restaurant.domain.inventory.InventoryOrderAction;
import com.delivery.restaurant_service.repository.MenuItemInventoryOrderReceiptRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.restaurant.inventory-enabled", havingValue = "true")
public class JpaInventoryReceiptAdapter implements InventoryReceiptPort {
    private final MenuItemInventoryOrderReceiptRepository receipts;
    private final boolean h2;
    public JpaInventoryReceiptAdapter(MenuItemInventoryOrderReceiptRepository receipts,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.receipts = receipts;
        this.h2 = datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2:");
    }
    @Override public int insertIfAbsent(Receipt row) {
        if (h2) return receipts.insertIfAbsentH2(row.eventId(), row.sourceTopic(), row.action().name(),
                row.orderId(), row.reservationId(), row.fingerprint());
        return receipts.insertIfAbsentPostgres(row.eventId(), row.sourceTopic(), row.action().name(),
                row.orderId(), row.reservationId(), row.fingerprint());
    }
    @Override public Optional<Receipt> find(UUID eventId) {
        return receipts.findById(eventId).map(row -> new Receipt(row.getEventId(), row.getSourceTopic(),
                InventoryOrderAction.valueOf(row.getAction()), row.getOrderId(), row.getReservationId(), row.getPayloadFingerprint()));
    }
}
