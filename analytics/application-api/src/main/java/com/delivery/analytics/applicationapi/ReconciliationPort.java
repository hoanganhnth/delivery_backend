package com.delivery.analytics.applicationapi;
import com.delivery.analytics.domain.OrderReconciliationAccumulator.Snapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public interface ReconciliationPort {
    record Receipt(String eventType, Long restaurantId, BigDecimal amount) {}
    record Page<T>(List<T> content, boolean hasNext) {}
    interface Scope {
        Long restaurantId();
        void overwrite(Snapshot snapshot);
    }
    Page<Receipt> receipts(LocalDate date, int page, int size);
    Page<Scope> scopes(LocalDate date, int page, int size);
    void overwrite(LocalDate date, Long restaurantId, Snapshot snapshot);
}
