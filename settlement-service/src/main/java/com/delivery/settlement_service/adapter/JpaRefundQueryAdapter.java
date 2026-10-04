package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.RefundPolicy;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.delivery.settlement_service.metrics.BusinessMetrics;
import org.springframework.data.domain.PageRequest;
import java.util.*;

public final class JpaRefundQueryAdapter implements RefundQueryStore {
    private final RefundCaseRepository repository;
    private final BusinessMetrics metrics;
    public JpaRefundQueryAdapter(RefundCaseRepository repository,BusinessMetrics metrics) {this.repository=repository;this.metrics=metrics;}
    @Override public List<AdminRefundCase> allAdmin(int limit) {return map(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0,limit)));}
    @Override public List<AdminRefundCase> adminByStatus(RefundPolicy.Status status,int limit) {
        return map(repository.findByStatusOrderByCreatedAtDesc(JpaLedgerAdapter.enumValue(status,RefundCase.RefundStatus.class),PageRequest.of(0,limit)));
    }
    @Override public Optional<AdminRefundCase> byId(UUID id) {return repository.findById(id).map(this::snapshot);}
    @Override public List<AdminRefundCase> legacyUser(Long user,int limit) {
        return map(repository.findByUserIdOrderByCreatedAtDesc(user,PageRequest.of(0,limit)));
    }
    @Override public List<AdminRefundCase> principal(Long principal,int limit) {
        return map(repository.findByUserPrincipalIdOrderByCreatedAtDesc(principal,PageRequest.of(0,limit)));
    }
    @Override public List<AdminRefundCase> principalOrUnmigratedLegacy(Long principal,Long user,int limit) {
        return map(repository.findByPrincipalOrUnmigratedLegacyUserOrderByCreatedAtDesc(principal,user,PageRequest.of(0,limit)));
    }
    @Override public void legacyFallback() {metrics.identityLegacyFallback("customer_refund_list");}
    private List<AdminRefundCase> map(List<RefundCase> rows) {return rows.stream().map(this::snapshot).toList();}
    private AdminRefundCase snapshot(RefundCase row) {
        return new AdminRefundCase(
                row.getRefundId(),
                row.getEventId(),
                row.getIdempotencyKey(),
                row.getOrderId(),
                row.getUserId(),
                row.getUserPrincipalId(),
                row.getRestaurantId(),
                row.getPreviousOrderStatus(),
                row.getCurrentOrderStatus(),
                row.getPaymentMethod(),
                row.getTrigger() == null ? null : row.getTrigger().name(),
                row.getComponent() == null ? null : row.getComponent().name(),
                row.getStatus() == null ? null : row.getStatus().name(),
                row.getCurrency(),
                row.getSubtotalAmount(),
                row.getDiscountAmount(),
                row.getShippingFee(),
                row.getTotalAmount(),
                row.getCapturedAmount(),
                row.getRefundAmount(),
                row.getActorSource(),
                row.getActorId(),
                row.getReason(),
                row.getProviderReference(),
                row.getLastError(),
                row.getAttempts(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getProcessedAt());
    }
}
