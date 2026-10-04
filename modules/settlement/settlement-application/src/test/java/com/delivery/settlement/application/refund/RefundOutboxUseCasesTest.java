package com.delivery.settlement.application.refund;

import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RefundOutboxUseCasesTest {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"),ZoneOffset.UTC);
    private static final LocalDateTime NOW=LocalDateTime.now(CLOCK);
    static class Writer implements RefundOutboxWritePort {
        boolean exists;int saves,checks;RefundOutboxIntent intent;
        @Override public boolean exists(UUID id) {checks++;return exists;}
        @Override public void save(RefundOutboxIntent value) {saves++;intent=value;exists=true;}
    }
    static class RelayStore implements RefundOutboxRelayPort {
        List<RefundOutboxDelivery> due=List.of();Map<UUID,LocalDateTime> sent=new HashMap<>();
        Map<UUID,RefundOutboxFailure> failures=new HashMap<>();int limit;LocalDateTime scan;
        @Override public List<RefundOutboxDelivery> lockDue(LocalDateTime now,int limit) {this.scan=now;this.limit=limit;return due;}
        @Override public void sent(UUID id,LocalDateTime at) {sent.put(id,at);}
        @Override public void failed(UUID id,RefundOutboxFailure decision,Exception cause) {failures.put(id,decision);}
    }
    private RefundOutboxRequest request() {return new RefundOutboxRequest(UUID.randomUUID(),10L,BigDecimal.TEN,"VND","ONLINE",
            RefundPolicy.Trigger.ORDER_CANCELLED,RefundPolicy.Status.REQUESTED);}
    private RefundOutboxDelivery delivery(int attempts) {return new RefundOutboxDelivery(UUID.randomUUID(),"refund.requested","10","payload",attempts,NOW);}
    @Test void enqueuePersistsOneStableIntentAndReplayDoesNotCreateAnother() {
        var writer=new Writer();var core=new DefaultRefundOutboxEnqueueUseCase(writer,CLOCK);var request=request();
        assertThat(core.enqueue(request)).isEqualTo(request.eventId());assertThat(writer.intent.occurredAt()).isEqualTo(NOW);
        assertThat(writer.intent.request()).isSameAs(request);assertThat(writer.saves).isEqualTo(1);
        assertThat(core.enqueue(request)).isEqualTo(request.eventId());assertThat(writer.saves).isEqualTo(1);assertThat(writer.checks).isEqualTo(2);
    }
    @Test void existingIntentIsReturnedWithoutSerializationOrPersistence() {
        var writer=new Writer();writer.exists=true;var request=request();
        assertThat(new DefaultRefundOutboxEnqueueUseCase(writer,CLOCK).enqueue(request)).isEqualTo(request.eventId());
        assertThat(writer.saves).isZero();assertThat(writer.intent).isNull();
    }
    @Test void relayPublishesLockedBoundedRowsAndMarksSuccessAfterPublish() {
        var store=new RelayStore();var first=delivery(0);var second=delivery(2);store.due=List.of(first,second);
        var published=new ArrayList<UUID>();
        new DefaultRefundOutboxRelayUseCase(store,row->{assertThat(store.sent).doesNotContainKey(row.eventId());published.add(row.eventId());},CLOCK).relay();
        assertThat(store.limit).isEqualTo(100);assertThat(store.scan).isEqualTo(NOW);
        assertThat(published).containsExactly(first.eventId(),second.eventId());assertThat(store.sent.values()).containsOnly(NOW);assertThat(store.failures).isEmpty();
    }
    @Test void oneFailedPublishSchedulesRetryOrDeadWithoutStoppingOtherRows() {
        var store=new RelayStore();var retry=delivery(0);var dead=delivery(11);var success=delivery(0);store.due=List.of(retry,dead,success);
        new DefaultRefundOutboxRelayUseCase(store,row->{if(!row.eventId().equals(success.eventId()))throw new Exception("publish failure");},CLOCK).relay();
        assertThat(store.sent).containsOnlyKeys(success.eventId());
        assertThat(store.failures.get(retry.eventId()).nextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(store.failures.get(retry.eventId()).dead()).isFalse();assertThat(store.failures.get(dead.eventId()).dead()).isTrue();
        assertThat(store.failures.get(dead.eventId()).attempts()).isEqualTo(12);
    }
    @Test void emptyDueQueueDoesNotPublish() {
        var store=new RelayStore();new DefaultRefundOutboxRelayUseCase(store,row->{throw new AssertionError("empty queue");},CLOCK).relay();
        assertThat(store.sent).isEmpty();assertThat(store.failures).isEmpty();
    }
}
