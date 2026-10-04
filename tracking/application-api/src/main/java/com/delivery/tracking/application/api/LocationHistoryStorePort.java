package com.delivery.tracking.application.api;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import java.time.Instant;
import java.util.*;
public interface LocationHistoryStorePort {
    Optional<LocationHistoryReceiptFacts> receipt(UUID eventId);
    int claim(LocationHistoryReceiptFacts receipt);
    int complete(UUID eventId, LocationHistoryOutcome outcome);
    void lockSampling(Long deliveryId, Long shipperId);
    Optional<LocationHistoryPoint> previous(Long deliveryId, Long shipperId, Instant occurredAt);
    Optional<LocationHistoryPoint> next(Long deliveryId, Long shipperId, Instant occurredAt);
    void save(LocationHistoryPoint point);
    List<LocationHistoryPoint> byDelivery(long deliveryId, int size);
    int deleteHistoryOlderThan(Instant cutoff);
    int deleteReceiptsOlderThan(Instant cutoff);
}
