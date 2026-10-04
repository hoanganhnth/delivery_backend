package com.delivery.tracking.application.api;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import java.time.Instant;
import java.util.List;
public interface LocationHistoryUseCase {
    LocationHistoryOutcome record(LocationHistoryCommand command);
    List<LocationHistoryPoint> byDelivery(long deliveryId, int requestedSize);
    LocationHistoryCleanupResult cleanup(Instant cutoff);
    LocationHistoryCleanupResult cleanupExpired();
}
