package com.delivery.settlement.application.refund;

import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.*;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultRefundCaseUseCase implements RefundCaseUseCase {
    private final RefundCaseStore store;
    private final boolean providerEnabled;
    private final Supplier<UUID> ids;
    public DefaultRefundCaseUseCase(RefundCaseStore store, boolean providerEnabled, Supplier<UUID> ids) {
        this.store=Objects.requireNonNull(store);this.providerEnabled=providerEnabled;this.ids=Objects.requireNonNull(ids);
    }
    @Override public RefundReceipt cancel(RefundPolicy.Cancellation event, Supplier<String> fingerprint) {
        RefundPolicy.validate(event);
        String hash=fingerprint.get();
        var trigger=RefundPolicy.resolveTrigger(event);
        String key=RefundDraft.key(event.orderId(),trigger);
        var existing=find(event.eventId(),key,event.orderId(),trigger);
        if(existing!=null) {existing.requireExact(event.eventId(),event.orderId(),key,hash,false);return existing;}
        return claim(RefundDraft.cancellation(ids.get(),event,hash,providerEnabled),false);
    }
    @Override public RefundReceipt deliveryException(RefundPolicy.DeliveryException event, Supplier<String> fingerprint) {
        RefundPolicy.validate(event);
        String hash=fingerprint.get();
        var trigger=RefundPolicy.Trigger.DELIVERY_DISPUTE;
        String key=RefundDraft.key(event.orderId(),trigger);
        var existing=find(event.eventId(),key,event.orderId(),trigger);
        if(existing!=null) {existing.requireExact(event.eventId(),event.orderId(),key,hash,true);return existing;}
        return claim(RefundDraft.deliveryException(ids.get(),event,hash),true);
    }
    private RefundReceipt find(UUID event, String key, Long order, RefundPolicy.Trigger trigger) {
        var receipt=store.findByEvent(event).orElse(null);
        if(receipt!=null)return receipt;
        receipt=store.findByKey(key).orElse(null);
        return receipt!=null ? receipt : store.findByOrder(order,trigger).orElse(null);
    }
    private RefundReceipt claim(RefundDraft draft, boolean dispute) {
        if(!store.claim(draft)) {
            var winner=find(draft.eventId(),draft.idempotencyKey(),draft.orderId(),draft.decision().trigger());
            if(winner==null)throw new IllegalStateException(dispute ? "delivery exception refund conflict resolved without a committed case"
                    : "refund case conflict resolved without a committed refund case");
            winner.requireExact(draft.eventId(),draft.orderId(),draft.idempotencyKey(),draft.fingerprint(),dispute);
            return winner;
        }
        if(draft.decision().status()==RefundPolicy.Status.REQUESTED)store.enqueue(draft);
        return draft.receipt();
    }
}
