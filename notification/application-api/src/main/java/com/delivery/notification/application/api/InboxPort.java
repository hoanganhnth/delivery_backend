package com.delivery.notification.application.api;

import com.delivery.notification.domain.InboxActor;
import java.time.LocalDateTime;
import java.util.List;

/** Adapter scopes every query by actor. N and R are opaque persistence/response handles. */
public interface InboxPort<N, R> {
    List<N> list(InboxActor actor, boolean unread, int limit);
    N owned(Long id, InboxActor actor);
    R response(N row);
    Boolean isRead(N row);
    void setRead(N row, LocalDateTime at);
    void save(N row);
    void saveAll(List<N> rows);
    void fallback(List<N> rows, String surface);
    long unreadCount(InboxActor actor);
    void delete(N row);
    void deleteLegacy(Long id, Long userId);
    void markLegacy(Long id, Long userId, LocalDateTime at);
    int markAllLegacy(Long userId, LocalDateTime at);
}
