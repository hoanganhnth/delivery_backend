package com.delivery.restaurant_service.service;

import com.delivery.observability.SafeLog;
import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Short DB transactions for Restaurant outbox leases; Kafka I/O stays outside. */
@Service
public class RestaurantOutboxLeaseService {
    private final RestaurantOutboxEventRepository repository;

    public RestaurantOutboxLeaseService(RestaurantOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<RestaurantOutboxEvent> claim(int batchSize, UUID leaseToken, long leaseSeconds) {
        LocalDateTime until = LocalDateTime.now().plusSeconds(Math.max(5, leaseSeconds));
        List<RestaurantOutboxEvent> events = repository.lockNextClaimableBatch(Math.max(1, Math.min(batchSize, 500)));
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        for (RestaurantOutboxEvent event : events) {
            event.setStatus(RestaurantOutboxEvent.Status.IN_FLIGHT);
            event.setLeaseToken(leaseToken);
            event.setLeaseUntil(until);
            repository.save(event);
        }
        return events;
    }

    @Transactional
    public boolean markSent(Long id, UUID token) {
        RestaurantOutboxEvent event = owned(id, token);
        if (event == null) return false;
        event.setStatus(RestaurantOutboxEvent.Status.SENT);
        event.setSentAt(LocalDateTime.now());
        event.setLastError(null);
        clearLease(event);
        repository.save(event);
        return true;
    }

    @Transactional
    public boolean markFailure(Long id, UUID token, Throwable failure, int maxAttempts) {
        RestaurantOutboxEvent event = owned(id, token);
        if (event == null) return false;
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(abbreviate(SafeLog.exceptionMessage(failure)));
        if (attempts >= Math.max(1, Math.min(maxAttempts, 100))) {
            event.setStatus(RestaurantOutboxEvent.Status.DEAD);
        } else {
            long delay = Math.min(300L, 1L << Math.min(Math.max(attempts - 1, 0), 8));
            event.setStatus(RestaurantOutboxEvent.Status.PENDING);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
        }
        clearLease(event);
        repository.save(event);
        return true;
    }

    private RestaurantOutboxEvent owned(Long id, UUID token) {
        if (id == null || token == null) return null;
        RestaurantOutboxEvent event = repository.findByIdForUpdate(id).orElse(null);
        return event != null && event.getStatus() == RestaurantOutboxEvent.Status.IN_FLIGHT
                && token.equals(event.getLeaseToken()) ? event : null;
    }

    private void clearLease(RestaurantOutboxEvent event) {
        event.setLeaseToken(null);
        event.setLeaseUntil(null);
    }

    private String abbreviate(String message) {
        if (message == null) return "Unknown Kafka publish failure";
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
