package com.delivery.settlement.application.refund;
import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.*;
import java.time.*;
import java.util.*;
public final class DefaultRefundOutboxEnqueueUseCase implements RefundOutboxEnqueueUseCase {
    private final RefundOutboxWritePort store;
    private final Clock clock;
    public DefaultRefundOutboxEnqueueUseCase(RefundOutboxWritePort store,Clock clock) {this.store=Objects.requireNonNull(store);this.clock=Objects.requireNonNull(clock);}
    @Override public UUID enqueue(RefundOutboxRequest request) {
        UUID id=request.eventId();
        if(store.exists(id))return id;
        store.save(new RefundOutboxIntent(request,id,request.eventType(),LocalDateTime.now(clock)));
        return id;
    }
}
