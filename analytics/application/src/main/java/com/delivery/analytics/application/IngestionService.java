package com.delivery.analytics.application;

import com.delivery.analytics.applicationapi.IngestionUseCase;
import com.delivery.analytics.applicationapi.IngestionPorts.Payloads;
import com.delivery.analytics.applicationapi.IngestionPorts.Projections;
import com.delivery.analytics.applicationapi.IngestionPorts.Receipts;
import java.time.LocalDate;
import java.util.function.Supplier;

/** Called inside the host transaction; payload/item validation timing is intentional. */
public final class IngestionService implements IngestionUseCase {
    private final Receipts receipts;
    private final Payloads payloads;
    private final Projections projections;
    private final Supplier<LocalDate> today;

    public IngestionService(Receipts receipts, Payloads payloads, Projections projections,
                            Supplier<LocalDate> today) {
        this.receipts = receipts;
        this.payloads = payloads;
        this.projections = projections;
        this.today = today;
    }

    @Override
    public boolean ingest(Command command) {
        var identity = command.identity();
        if (!receipts.claim(command.deduplicationKey(), identity)) return false;
        LocalDate date = today.get();
        String type = identity.eventType();
        switch (type) {
            case "ORDER_CREATED", "ORDER_CANCELLED":
                LocalDate itemDate = payloads.eventDate(identity.rawPayload(), date);
                order(command, date);
                if (projections.itemsEnabled(identity.restaurantId())) {
                    var items = payloads.items(identity.rawPayload());
                    for (var item : items) {
                        projections.item(itemDate, identity.restaurantId(), item, type.equals("ORDER_CANCELLED"));
                    }
                }
                break;
            case "ORDER_DELIVERED":
                order(command, date);
                break;
            case "PAYMENT_COMPLETED", "PAYMENT_FAILED":
                projections.payment(type, date, identity.amount());
                break;
            default:
                throw new IllegalArgumentException("Unsupported analytics event type: " + type);
        }
        return true;
    }

    private void order(Command command, LocalDate date) {
        var identity = command.identity();
        projections.order(identity.eventType(), date, null, identity.amount());
        if (identity.restaurantId() != null) {
            projections.order(identity.eventType(), date, identity.restaurantId(), identity.amount());
        }
    }
}
