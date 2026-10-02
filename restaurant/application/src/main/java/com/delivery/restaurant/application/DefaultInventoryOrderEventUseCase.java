package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.InventoryReceiptPort.Receipt;
import com.delivery.restaurant.domain.inventory.InventoryOrderAction;
import java.util.Objects;

/** Atomic receipt admission, exact replay and order-driven reservation transitions. */
public final class DefaultInventoryOrderEventUseCase implements InventoryOrderEventUseCase {
    private final InventoryReceiptPort receipts;
    private final MenuItemInventoryUseCase inventory;
    private final RestaurantTransactionPort transactions;

    public DefaultInventoryOrderEventUseCase(InventoryReceiptPort receipts,
            MenuItemInventoryUseCase inventory, RestaurantTransactionPort transactions) {
        this.receipts = Objects.requireNonNull(receipts);
        this.inventory = Objects.requireNonNull(inventory);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override public void consume(InventoryOrderEventCommand command) {
        transactions.required(() -> {
            var action = switch (command.source()) {
                case ORDER_CREATED -> InventoryOrderAction.COMMIT;
                case ORDER_CANCELLED, REFUND_ELIGIBLE -> InventoryOrderAction.RELEASE;
            };
            var incoming = new Receipt(command.eventId(), command.sourceTopic(), action,
                    command.orderId(), command.reservationId(), command.fingerprint());
            if (receipts.insertIfAbsent(incoming) == 0) {
                var existing = receipts.find(command.eventId()).orElseThrow(() -> new IllegalStateException(
                        "inventory receipt conflict resolved without a committed receipt"));
                if (!existing.sourceTopic().equals(incoming.sourceTopic())
                        || existing.action() != incoming.action()
                        || !existing.orderId().equals(incoming.orderId())
                        || !Objects.equals(existing.reservationId(), incoming.reservationId())
                        || !existing.fingerprint().equals(incoming.fingerprint())) {
                    throw new IllegalArgumentException("eventId replay has a contradictory inventory reservation payload");
                }
                return null;
            }
            if (command.reservationId() != null) {
                if (action == InventoryOrderAction.COMMIT) inventory.commit(command.reservationId(), command.orderId());
                else inventory.release(command.reservationId(), command.orderId());
            }
            return null;
        });
    }
}
