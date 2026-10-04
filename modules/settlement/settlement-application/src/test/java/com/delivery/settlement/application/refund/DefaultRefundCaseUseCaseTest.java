package com.delivery.settlement.application.refund;

import com.delivery.settlement.application.api.refund.RefundCaseStore;
import com.delivery.settlement.domain.refund.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultRefundCaseUseCaseTest {
    private RefundPolicy.Cancellation event() {
        return new RefundPolicy.Cancellation(UUID.randomUUID(),"ORDER_CANCELLED",1L,2L,null,3L,"PENDING",
                "CANCELLED","cancel",null,"SYSTEM","SYSTEM_CANCELLED","ONLINE",
                BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ONE,new BigDecimal("11"));
    }
    private RefundPolicy.DeliveryException exception() {
        return new RefundPolicy.DeliveryException(UUID.randomUUID(),"DELIVERY_EXCEPTION_REPORTED",UUID.randomUUID(),
                4L,1L,2L,null,3L,5L,"PICKED_UP","PICKED_UP","RETRY_AVAILABLE","failed","ONLINE",
                BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ONE,new BigDecimal("11"));
    }
    static class Store implements RefundCaseStore {
        RefundReceipt byEvent, byKey, byOrder, winner; RefundDraft draft;
        boolean inserted=true; int claims,enqueues; List<String> reads=new ArrayList<>();
        @Override public Optional<RefundReceipt> findByEvent(UUID id) {reads.add("event");return Optional.ofNullable(claims>0 ? winner : byEvent);}
        @Override public Optional<RefundReceipt> findByKey(String key) {reads.add("key");return Optional.ofNullable(byKey);}
        @Override public Optional<RefundReceipt> findByOrder(Long order,RefundPolicy.Trigger trigger) {reads.add("order");return Optional.ofNullable(byOrder);}
        @Override public boolean claim(RefundDraft value) {draft=value;claims++;return inserted;}
        @Override public void enqueue(RefundDraft value) {assertThat(value).isSameAs(draft);enqueues++;}
    }
    private DefaultRefundCaseUseCase core(Store store,boolean enabled) {return new DefaultRefundCaseUseCase(store,enabled,UUID::randomUUID);}
    @Test void newOnlineRequestClaimsReceiptBeforeOneOutboxIntent() {
        var store=new Store();var e=event();var result=core(store,true).cancel(e,()->"hash");
        assertThat(result).isEqualTo(store.draft.receipt());assertThat(store.reads).containsExactly("event","key","order");
        assertThat(store.claims).isEqualTo(1);assertThat(store.enqueues).isEqualTo(1);
        assertThat(store.draft.decision().refundAmount()).isEqualByComparingTo("11");
    }
    @Test void providerOffAndDeliveryDisputesNeverEnqueue() {
        var disabled=new Store();core(disabled,false).cancel(event(),()->"hash");assertThat(disabled.enqueues).isZero();
        var dispute=new Store();core(dispute,true).deliveryException(exception(),()->"hash");
        assertThat(dispute.draft.decision().status()).isEqualTo(RefundPolicy.Status.MANUAL_REVIEW);
        assertThat(dispute.enqueues).isZero();assertThat(dispute.claims).isEqualTo(1);
    }
    @Test void replayAtEachIdentityIndexDoesNotClaimOrEnqueue() {
        var e=event();var receipt=RefundDraft.cancellation(UUID.randomUUID(),e,"hash",true).receipt();
        for(int index=0;index<3;index++) {
            var store=new Store();if(index==0)store.byEvent=receipt;else if(index==1)store.byKey=receipt;else store.byOrder=receipt;
            assertThat(core(store,true).cancel(e,()->"hash")).isSameAs(receipt);
            assertThat(store.claims).isZero();assertThat(store.enqueues).isZero();
        }
        var ex=exception();var store=new Store();store.byEvent=RefundDraft.deliveryException(UUID.randomUUID(),ex,"hash").receipt();
        assertThat(core(store,true).deliveryException(ex,()->"hash")).isSameAs(store.byEvent);assertThat(store.claims).isZero();
        assertThatThrownBy(()->core(store,true).deliveryException(ex,()->"different")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void concurrentClaimWinnerMustBeExactAndNeverCreatesAnotherOutboxIntent() {
        var e=event();var store=new Store();store.inserted=false;store.winner=RefundDraft.cancellation(UUID.randomUUID(),e,"hash",true).receipt();
        assertThat(core(store,true).cancel(e,()->"hash")).isSameAs(store.winner);assertThat(store.enqueues).isZero();
        var ex=exception();store=new Store();store.inserted=false;store.winner=RefundDraft.deliveryException(UUID.randomUUID(),ex,"hash").receipt();
        assertThat(core(store,true).deliveryException(ex,()->"hash")).isSameAs(store.winner);assertThat(store.enqueues).isZero();
        var contradictory=new Store();contradictory.inserted=false;
        contradictory.winner=RefundDraft.cancellation(UUID.randomUUID(),e,"wrong",true).receipt();
        assertThatThrownBy(()->core(contradictory,true).cancel(e,()->"hash")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refund event replay has a contradictory payload");assertThat(contradictory.enqueues).isZero();
        var contradictoryDispute=new Store();contradictoryDispute.inserted=false;
        contradictoryDispute.winner=RefundDraft.deliveryException(UUID.randomUUID(),ex,"wrong").receipt();
        assertThatThrownBy(()->core(contradictoryDispute,true).deliveryException(ex,()->"hash")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delivery exception refund replay has a contradictory payload");assertThat(contradictoryDispute.enqueues).isZero();
    }
    @Test void missingClaimWinnerAndContradictoryReplayFailClosed() {
        var missing=new Store();missing.inserted=false;
        assertThatThrownBy(()->core(missing,true).cancel(event(),()->"hash")).isInstanceOf(IllegalStateException.class)
                .hasMessage("refund case conflict resolved without a committed refund case");
        var missingDispute=new Store();missingDispute.inserted=false;
        assertThatThrownBy(()->core(missingDispute,true).deliveryException(exception(),()->"hash")).isInstanceOf(IllegalStateException.class)
                .hasMessage("delivery exception refund conflict resolved without a committed case");
        var e=event();var conflict=new Store();conflict.byEvent=RefundDraft.cancellation(UUID.randomUUID(),e,"hash",true).receipt();
        assertThatThrownBy(()->core(conflict,true).cancel(e,()->"other")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refund event replay has a contradictory payload");assertThat(conflict.claims).isZero();
    }
    @Test void invalidSnapshotsAreRejectedBeforeFingerprintingOrPersistence() {
        var store=new Store();
        assertThatThrownBy(()->core(store,true).cancel(null,()->{throw new AssertionError("must not fingerprint invalid event");}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->core(store,true).deliveryException(null,()->{throw new AssertionError("must not fingerprint invalid event");}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.reads).isEmpty();
    }
}
