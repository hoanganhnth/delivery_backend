package com.delivery.analytics.applicationapi;
import com.delivery.analytics.domain.ReceiptIdentity;
import com.delivery.analytics.domain.SnapshotDecisions.Item;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public final class IngestionPorts {
    private IngestionPorts() {}
    public interface Receipts { boolean claim(String key, ReceiptIdentity identity); }
    public interface Payloads {
        LocalDate eventDate(String payload, LocalDate fallback);
        List<Item> items(String payload);
    }
    public interface Projections {
        void order(String type, LocalDate date, Long restaurantId, BigDecimal amount);
        void payment(String type, LocalDate date, BigDecimal amount);
        boolean itemsEnabled(Long restaurantId);
        void item(LocalDate date, Long restaurantId, Item item, boolean cancelled);
    }
}
