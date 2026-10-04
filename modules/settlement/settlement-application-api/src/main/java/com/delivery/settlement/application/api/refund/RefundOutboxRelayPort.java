package com.delivery.settlement.application.api.refund;
import com.delivery.settlement.domain.refund.*;
import java.time.LocalDateTime;
import java.util.*;
public interface RefundOutboxRelayPort {
    List<RefundOutboxDelivery> lockDue(LocalDateTime now,int limit);
    void sent(UUID id,LocalDateTime at);
    void failed(UUID id,RefundOutboxFailure decision,Exception cause);
}
