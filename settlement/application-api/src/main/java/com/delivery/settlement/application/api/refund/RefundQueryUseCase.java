package com.delivery.settlement.application.api.refund;

import com.delivery.settlement.domain.refund.RefundPolicy;
import java.util.*;

public interface RefundQueryUseCase {
    int READ_LIMIT=100;
    List<AdminRefundCase> adminCases(RefundPolicy.Status status,int requestedLimit);
    AdminRefundCase adminCase(UUID id);
    List<CustomerRefundCase> customerCases(Long userId,int requestedLimit);
    List<CustomerRefundCase> customerCases(Long principalId,Long legacyUserId,int requestedLimit);
}
