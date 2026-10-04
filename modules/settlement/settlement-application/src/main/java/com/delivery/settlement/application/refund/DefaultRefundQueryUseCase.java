package com.delivery.settlement.application.refund;

import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.RefundPolicy;
import java.util.*;

public final class DefaultRefundQueryUseCase implements RefundQueryUseCase {
    private final RefundQueryStore store;
    private final boolean principalOwnershipEnforced;
    public DefaultRefundQueryUseCase(RefundQueryStore store,boolean principalOwnershipEnforced) {
        this.store=Objects.requireNonNull(store);this.principalOwnershipEnforced=principalOwnershipEnforced;
    }
    @Override public List<AdminRefundCase> adminCases(RefundPolicy.Status status,int requestedLimit) {
        int limit=limit(requestedLimit);
        return status==null ? store.allAdmin(limit) : store.adminByStatus(status,limit);
    }
    @Override public AdminRefundCase adminCase(UUID id) {
        if(id==null)throw new IllegalArgumentException("refundId is required");
        return store.byId(id).orElseThrow(()->new RefundCaseMissing(id));
    }
    @Override public List<CustomerRefundCase> customerCases(Long userId,int requestedLimit) {
        if(userId==null || userId<=0)throw new IllegalArgumentException("userId is required");
        return customerProjection(store.legacyUser(userId,limit(requestedLimit)));
    }
    @Override public List<CustomerRefundCase> customerCases(Long principalId,Long legacyUserId,int requestedLimit) {
        if(principalId==null || principalId<=0 || legacyUserId==null || legacyUserId<=0)
            throw new IllegalArgumentException("principalId and legacyUserId are required");
        int limit=limit(requestedLimit);
        var rows=principalOwnershipEnforced ? store.principal(principalId,limit)
                : store.principalOrUnmigratedLegacy(principalId,legacyUserId,limit);
        if(!principalOwnershipEnforced) rows.stream().filter(row->row.userPrincipalId()==null).forEach(row->store.legacyFallback());
        return customerProjection(rows);
    }
    private int limit(int requested) {return Math.min(Math.max(requested,1),READ_LIMIT);}
    private List<CustomerRefundCase> customerProjection(List<AdminRefundCase> rows) {
        return rows.stream().map(row->new CustomerRefundCase(row.refundId(),row.orderId(),row.paymentMethod(),row.trigger(),
                row.status(),row.currency(),row.refundAmount(),row.createdAt(),row.updatedAt(),row.processedAt())).toList();
    }
}
