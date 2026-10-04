package com.delivery.settlement.application.api.refund;
import com.delivery.settlement.domain.refund.RefundOutboxIntent;
import java.util.UUID;
public interface RefundOutboxWritePort {
    boolean exists(UUID id);
    void save(RefundOutboxIntent intent);
}
