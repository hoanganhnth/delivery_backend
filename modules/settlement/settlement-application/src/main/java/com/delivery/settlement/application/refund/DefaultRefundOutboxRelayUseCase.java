package com.delivery.settlement.application.refund;
import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.*;
import java.time.*;
import java.util.*;
public final class DefaultRefundOutboxRelayUseCase implements RefundOutboxRelayUseCase {
    private final RefundOutboxRelayPort store;
    private final RefundOutboxPublisher publisher;
    private final Clock clock;
    public DefaultRefundOutboxRelayUseCase(RefundOutboxRelayPort store,RefundOutboxPublisher publisher,Clock clock) {
        this.store=Objects.requireNonNull(store);this.publisher=Objects.requireNonNull(publisher);this.clock=Objects.requireNonNull(clock);
    }
    @Override public void relay() {
        for(var row:store.lockDue(LocalDateTime.now(clock),SCAN_LIMIT)) {
            try {publisher.publish(row);store.sent(row.eventId(),LocalDateTime.now(clock));}
            catch(Exception failure) {store.failed(row.eventId(),RefundOutboxFailure.next(row.attempts(),row.nextAttemptAt(),LocalDateTime.now(clock),failure.getMessage()),failure);}
        }
    }
}
