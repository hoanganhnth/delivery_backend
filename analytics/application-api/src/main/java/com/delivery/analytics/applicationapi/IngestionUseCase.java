package com.delivery.analytics.applicationapi;
import com.delivery.analytics.domain.ReceiptIdentity;
public interface IngestionUseCase {
    /** Returns false for an exact replay, true only after all projections succeed. */
    boolean ingest(Command command);
    record Command(String deduplicationKey, ReceiptIdentity identity) {}
}
