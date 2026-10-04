package com.delivery.settlement.application.api.refund;

import com.delivery.settlement.domain.refund.RefundPolicy;
import java.util.*;

public interface RefundQueryStore {
    List<AdminRefundCase> allAdmin(int limit);
    List<AdminRefundCase> adminByStatus(RefundPolicy.Status status,int limit);
    Optional<AdminRefundCase> byId(UUID id);
    List<AdminRefundCase> legacyUser(Long userId,int limit);
    List<AdminRefundCase> principal(Long principalId,int limit);
    List<AdminRefundCase> principalOrUnmigratedLegacy(Long principalId,Long userId,int limit);
    void legacyFallback();
}
