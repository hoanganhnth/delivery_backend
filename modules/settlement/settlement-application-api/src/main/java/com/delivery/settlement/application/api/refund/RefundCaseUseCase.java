package com.delivery.settlement.application.api.refund;

import com.delivery.settlement.domain.refund.*;
import java.util.function.Supplier;

public interface RefundCaseUseCase {
    RefundReceipt cancel(RefundPolicy.Cancellation event, Supplier<String> fingerprint);
    RefundReceipt deliveryException(RefundPolicy.DeliveryException event, Supplier<String> fingerprint);
}
