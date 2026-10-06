package com.delivery.notification.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NotificationLifecycleTest {
    private ReplayPayload payload(Long user, String title, String message, String type, String priority, Long related, String entity) {
        return new ReplayPayload(user, null, title, message, type, priority, related, entity, null);
    }
    @Test void validationPreservesOrderMessagesAndAcceptsUnconstrainedFields() {
        assertEquals("Send notification request is required", assertThrows(IllegalArgumentException.class,
                () -> NotificationLifecycle.validate(null)).getMessage());
        for (Long id : new Long[]{null, 0L, -1L}) assertEquals("userId must be positive",
                assertThrows(IllegalArgumentException.class, () -> NotificationLifecycle.validate(payload(id, null, null, null, null, null, null))).getMessage());
        for (String blank : new String[]{null, "", " \t\n"}) {
            assertInvalid(payload(1L, blank, null, null, null, null, null), "title");
            assertInvalid(payload(1L, "t", blank, null, null, null, null), "message");
            assertInvalid(payload(1L, "t", "m", blank, null, null, null), "type");
            assertInvalid(payload(1L, "t", "m", "UNKNOWN", blank, null, null), "priority");
        }
        assertDoesNotThrow(() -> NotificationLifecycle.validate(payload(1L, " t ", " m ", "UNKNOWN", "ANY", -1L, null)));
    }
    private void assertInvalid(ReplayPayload payload, String field) {
        assertEquals(field + " is required", assertThrows(IllegalArgumentException.class,
                () -> NotificationLifecycle.validate(payload)).getMessage());
    }
    @Test void keysAndDeliveryStatesRetainLegacySemantics() {
        for (String key : new String[]{null, "", " \n"}) assertFalse(NotificationLifecycle.hasKey(key));
        assertTrue(NotificationLifecycle.hasKey(" key "));
        assertTrue(NotificationLifecycle.retryPending("PENDING"));
        for (String status : new String[]{null, "SENT", "FAILED", "READ", "DELIVERED", "pending", ""}) {
            assertFalse(NotificationLifecycle.retryPending(status));
            if ("SENT".equals(status)) assertFalse(NotificationLifecycle.shouldDeliver(4L, status));
            else assertEquals("Notification 4 is not deliverable from status " + status,
                    assertThrows(IllegalStateException.class, () -> NotificationLifecycle.shouldDeliver(4L, status)).getMessage());
        }
        assertTrue(NotificationLifecycle.shouldDeliver(4L, "PENDING"));
    }
    @Test void wakeDataPreservesOptionalRelatedFieldsIncludingNullType() {
        var base = payload(1L, "t", "m", "TYPE", "P", null, "IGNORED");
        assertEquals(java.util.Map.of("notificationId", "4", "type", "TYPE"), NotificationLifecycle.pushData(4L, base));
        var data = NotificationLifecycle.pushData(4L, payload(1L, "t", "m", "TYPE", "P", 8L, null));
        assertEquals(4, data.size()); assertEquals("8", data.get("relatedEntityId"));
        assertTrue(data.containsKey("relatedEntityType")); assertNull(data.get("relatedEntityType"));
        assertEquals("ORDER", NotificationLifecycle.pushData(4L, payload(1L, "t", "m", "TYPE", "P", 8L, "ORDER")).get("relatedEntityType"));
    }
    @Test void inboxActorValidationAndReadRules() {
        assertEquals(100, InboxActor.listLimit());
        assertTrue(InboxActor.needsRead(null)); assertTrue(InboxActor.needsRead(false)); assertFalse(InboxActor.needsRead(true));
        for (boolean legacy : new boolean[]{false, true}) for (boolean enforced : new boolean[]{false, true}) {
            var actor = new InboxActor(1L, 2L, legacy, enforced);
            assertDoesNotThrow(actor::validate); assertEquals(!legacy && !enforced, actor.recordFallback());
        }
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertEquals("userId must be positive", assertThrows(IllegalArgumentException.class, () -> new InboxActor(null, id, true, false).validate()).getMessage());
            assertEquals("principalId must be positive", assertThrows(IllegalArgumentException.class, () -> new InboxActor(id, id, false, true).validate()).getMessage());
            assertEquals("legacyUserId must be positive", assertThrows(IllegalArgumentException.class, () -> new InboxActor(1L, id, false, true).validate()).getMessage());
        }
    }
}
