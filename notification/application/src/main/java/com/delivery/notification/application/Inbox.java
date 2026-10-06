package com.delivery.notification.application;

import com.delivery.notification.application.api.InboxPort;
import com.delivery.notification.domain.InboxActor;
import com.delivery.notification.domain.NotificationLifecycle;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/** Transactions for read mutations remain on the host entrypoints. */
public final class Inbox<N, R> {
    private final InboxPort<N, R> port;
    private final Supplier<LocalDateTime> now;

    public Inbox(InboxPort<N, R> port, Supplier<LocalDateTime> now) { this.port = port; this.now = now; }

    public List<R> list(InboxActor actor, boolean unread) {
        actor.validate();
        var rows = port.list(actor, unread, InboxActor.listLimit());
        fallback(actor, rows, unread ? "inbox_unread_list" : "inbox_list");
        return rows == null ? java.util.Collections.emptyList()
                : rows.stream().map(port::response).collect(java.util.stream.Collectors.toList());
    }

    public R get(Long id, InboxActor actor) {
        validate(id, actor);
        var row = port.owned(id, actor);
        fallback(actor, List.of(row), "inbox_read");
        return port.response(row);
    }

    public R markRead(Long id, InboxActor actor) {
        validate(id, actor);
        if (actor.legacyOnly()) {
            port.markLegacy(id, actor.legacyUserId(), now.get());
            return port.response(port.owned(id, actor));
        }
        var row = port.owned(id, actor);
        fallback(actor, List.of(row), "inbox_mark_read");
        if (InboxActor.needsRead(port.isRead(row))) {
            port.setRead(row, now.get());
            port.save(row);
        }
        return port.response(row);
    }

    public int markAllRead(InboxActor actor) {
        actor.validate();
        if (actor.legacyOnly()) return port.markAllLegacy(actor.legacyUserId(), now.get());
        var rows = port.list(actor, true, InboxActor.listLimit());
        fallback(actor, rows, "inbox_mark_all_read");
        var at = now.get();
        rows.forEach(row -> port.setRead(row, at));
        port.saveAll(rows);
        return rows.size();
    }

    public long unreadCount(InboxActor actor) { actor.validate(); return port.unreadCount(actor); }

    public void delete(Long id, InboxActor actor) {
        validate(id, actor);
        if (actor.legacyOnly()) { port.deleteLegacy(id, actor.legacyUserId()); return; }
        var row = port.owned(id, actor);
        fallback(actor, List.of(row), "inbox_delete");
        port.delete(row);
    }

    private void fallback(InboxActor actor, List<N> rows, String surface) {
        if (actor.recordFallback()) port.fallback(rows, surface);
    }

    private static void validate(Long id, InboxActor actor) {
        NotificationLifecycle.requirePositiveId(id, "notificationId");
        actor.validate();
    }
}
