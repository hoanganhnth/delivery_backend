package com.delivery.settlement.application.api.refund;
import com.delivery.settlement.domain.refund.RefundOutboxRequest;
import java.util.UUID;
public interface RefundOutboxEnqueueUseCase {
    UUID enqueue(RefundOutboxRequest request);
}
