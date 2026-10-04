package com.delivery.settlement_service.adapter;

import com.delivery.settlement.application.api.refund.RefundCaseStore;
import com.delivery.settlement.domain.refund.*;
import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundCase.RefundComponent;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.entity.RefundCase.RefundTrigger;
import com.delivery.settlement_service.repository.RefundCaseRepository;
import com.delivery.settlement_service.service.RefundOutboxService;
import java.util.*;

/** One invocation owns this adapter, retaining entity identity without shared mutable state. */
public final class JpaRefundCaseAdapter implements RefundCaseStore {
    private final RefundCaseRepository repository;
    private final RefundOutboxService outbox;
    private final String dataSourceUrl;
    private final Map<UUID,RefundCase> rows = new HashMap<>();
    public JpaRefundCaseAdapter(RefundCaseRepository repository, RefundOutboxService outbox, String dataSourceUrl) {
        this.repository=repository;this.outbox=outbox;this.dataSourceUrl=dataSourceUrl;
    }
    @Override public Optional<RefundReceipt> findByEvent(UUID eventId) {return repository.findByEventId(eventId).map(this::receipt);}
    @Override public Optional<RefundReceipt> findByKey(String key) {return repository.findByIdempotencyKey(key).map(this::receipt);}
    @Override public Optional<RefundReceipt> findByOrder(Long orderId, RefundPolicy.Trigger trigger) {
        return repository.findByOrderIdAndTriggerAndComponent(orderId,JpaLedgerAdapter.enumValue(trigger,RefundTrigger.class),
                RefundComponent.ORDER_TOTAL).map(this::receipt);
    }
    @Override public boolean claim(RefundDraft draft) {
        var row=RefundCase.builder().refundId(draft.refundId()).eventId(draft.eventId()).idempotencyKey(draft.idempotencyKey())
                .orderId(draft.orderId()).userId(draft.userId()).userPrincipalId(draft.userPrincipalId()).restaurantId(draft.restaurantId())
                .previousOrderStatus(draft.previousStatus()).currentOrderStatus(draft.currentStatus()).paymentMethod(draft.paymentMethod())
                .trigger(JpaLedgerAdapter.enumValue(draft.decision().trigger(),RefundTrigger.class)).component(RefundComponent.ORDER_TOTAL)
                .status(JpaLedgerAdapter.enumValue(draft.decision().status(),RefundStatus.class)).currency("VND")
                .subtotalAmount(draft.subtotal()).discountAmount(draft.discount()).shippingFee(draft.shipping()).totalAmount(draft.total())
                .capturedAmount(draft.decision().capturedAmount()).refundAmount(draft.decision().refundAmount())
                .actorSource(draft.decision().actorSource()).actorId(draft.actorId()).reason(draft.reason())
                .payloadFingerprint(draft.fingerprint()).attempts(0).build();
        rows.put(row.getRefundId(),row);
        return insertIfAbsent(row)!=0;
    }
    @Override public void enqueue(RefundDraft draft) {outbox.enqueue(requireEntity(draft.refundId()));}
    public RefundCase requireEntity(UUID id) {
        return Objects.requireNonNull(rows.get(id),"Refund row was not acquired by this invocation");
    }
    private RefundReceipt receipt(RefundCase row) {
        rows.put(row.getRefundId(),row);
        return new RefundReceipt(row.getRefundId(),row.getEventId(),row.getIdempotencyKey(),row.getOrderId(),
                JpaLedgerAdapter.enumValue(row.getTrigger(),RefundPolicy.Trigger.class),row.getPayloadFingerprint());
    }
    private int insertIfAbsent(RefundCase refundCase) {
        if (dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:h2:")) {
            return insertIfAbsentH2(refundCase);
        }
        return insertIfAbsentPostgres(refundCase);
    }

    private int insertIfAbsentPostgres(RefundCase refundCase) {
        if (refundCase.getUserPrincipalId() == null) {
            return repository.insertIfAbsentPostgres(
                    refundCase.getRefundId(), refundCase.getEventId(), refundCase.getIdempotencyKey(),
                    refundCase.getOrderId(), refundCase.getUserId(), refundCase.getRestaurantId(),
                    refundCase.getPreviousOrderStatus(), refundCase.getCurrentOrderStatus(),
                    refundCase.getPaymentMethod(), refundCase.getTrigger().name(), refundCase.getComponent().name(),
                    refundCase.getStatus().name(), refundCase.getCurrency(), refundCase.getSubtotalAmount(),
                    refundCase.getDiscountAmount(), refundCase.getShippingFee(), refundCase.getTotalAmount(),
                    refundCase.getCapturedAmount(), refundCase.getRefundAmount(), refundCase.getActorSource(),
                    refundCase.getActorId(), refundCase.getReason(), refundCase.getPayloadFingerprint(),
                    refundCase.getAttempts());
        }
        return repository.insertIfAbsentPostgres(
                refundCase.getRefundId(), refundCase.getEventId(), refundCase.getIdempotencyKey(),
                refundCase.getOrderId(), refundCase.getUserId(), refundCase.getUserPrincipalId(), refundCase.getRestaurantId(),
                refundCase.getPreviousOrderStatus(), refundCase.getCurrentOrderStatus(),
                refundCase.getPaymentMethod(), refundCase.getTrigger().name(), refundCase.getComponent().name(),
                refundCase.getStatus().name(), refundCase.getCurrency(), refundCase.getSubtotalAmount(),
                refundCase.getDiscountAmount(), refundCase.getShippingFee(), refundCase.getTotalAmount(),
                refundCase.getCapturedAmount(), refundCase.getRefundAmount(), refundCase.getActorSource(),
                refundCase.getActorId(), refundCase.getReason(), refundCase.getPayloadFingerprint(),
                refundCase.getAttempts());
    }

    private int insertIfAbsentH2(RefundCase refundCase) {
        return repository.insertIfAbsentH2(
                refundCase.getRefundId(), refundCase.getEventId(), refundCase.getIdempotencyKey(),
                refundCase.getOrderId(), refundCase.getUserId(), refundCase.getUserPrincipalId(), refundCase.getRestaurantId(),
                refundCase.getPreviousOrderStatus(), refundCase.getCurrentOrderStatus(),
                refundCase.getPaymentMethod(), refundCase.getTrigger().name(), refundCase.getComponent().name(),
                refundCase.getStatus().name(), refundCase.getCurrency(), refundCase.getSubtotalAmount(),
                refundCase.getDiscountAmount(), refundCase.getShippingFee(), refundCase.getTotalAmount(),
                refundCase.getCapturedAmount(), refundCase.getRefundAmount(), refundCase.getActorSource(),
                refundCase.getActorId(), refundCase.getReason(), refundCase.getPayloadFingerprint(),
                refundCase.getAttempts());
    }

}
