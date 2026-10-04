package com.delivery.settlement.domain.refund;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefundOutboxPolicyTest {
    @Test void eventIdentityAndIntentRetainExistingRefundStatusContract() {
        var request=new RefundOutboxRequest(UUID.randomUUID(),10L,BigDecimal.TEN,"VND","ONLINE",
                RefundPolicy.Trigger.ORDER_CANCELLED,RefundPolicy.Status.REQUESTED);
        assertEquals("REFUND_REQUESTED",request.eventType());
        assertEquals(UUID.nameUUIDFromBytes((request.refundId()+":REFUND_REQUESTED").getBytes(StandardCharsets.UTF_8)),request.eventId());
        var now=LocalDateTime.of(2026,10,4,12,0);var intent=new RefundOutboxIntent(request,request.eventId(),request.eventType(),now);
        assertEquals("REFUND_CASE",intent.aggregateType());assertEquals(request.refundId().toString(),intent.aggregateId());
        assertEquals("10",intent.eventKey());assertEquals(now,intent.occurredAt());assertEquals(BigDecimal.TEN,intent.request().amount());
    }
    @Test void failurePolicyPreservesDelayErrorBoundAndTwelfthAttemptDeadLetter() {
        var now=LocalDateTime.of(2026,10,4,12,0);
        var first=RefundOutboxFailure.next(0,now,now,null);
        assertEquals(1,first.attempts());assertEquals("Kafka publish failed",first.lastError());assertFalse(first.dead());
        assertEquals(now.plusSeconds(2),first.nextAttemptAt());
        var capped=RefundOutboxFailure.next(7,now,now,"x".repeat(2100));
        assertEquals(2000,capped.lastError().length());assertEquals(now.plusSeconds(256),capped.nextAttemptAt());
        var lastRetry=RefundOutboxFailure.next(10,now,now,"failure");assertFalse(lastRetry.dead());assertEquals(now.plusSeconds(256),lastRetry.nextAttemptAt());
        var dead=RefundOutboxFailure.next(11,now.minusSeconds(1),now,"failure");
        assertTrue(dead.dead());assertEquals(12,dead.attempts());assertEquals(now.minusSeconds(1),dead.nextAttemptAt());assertEquals("failure",dead.lastError());
    }
}
