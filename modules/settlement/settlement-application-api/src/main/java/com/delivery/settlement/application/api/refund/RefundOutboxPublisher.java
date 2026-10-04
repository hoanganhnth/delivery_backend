package com.delivery.settlement.application.api.refund;
import com.delivery.settlement.domain.refund.RefundOutboxDelivery;
public interface RefundOutboxPublisher {
    void publish(RefundOutboxDelivery message) throws Exception;
}
