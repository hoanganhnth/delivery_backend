package com.delivery.notification_service.service.impl;

import com.delivery.notification.application.api.InboxPort;
import com.delivery.notification.domain.InboxActor;
import com.delivery.notification_service.dto.response.NotificationResponse;
import com.delivery.notification_service.entity.Notification;
import com.delivery.notification_service.exception.NotificationNotFoundException;
import com.delivery.notification_service.mapper.NotificationMapper;
import com.delivery.notification_service.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence, ownership query selection and metrics stay in the host adapter. */
final class NotificationInboxAdapter implements InboxPort<Notification, NotificationResponse> {
    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final MeterRegistry metrics;

    NotificationInboxAdapter(NotificationRepository repository, NotificationMapper mapper, MeterRegistry metrics) {
        this.repository = repository; this.mapper = mapper; this.metrics = metrics;
    }

    public List<Notification> list(InboxActor actor, boolean unread, int limit) {
        var page = PageRequest.of(0, limit);
        if (actor.legacyOnly()) return unread
                ? repository.findByUserIdAndIsReadOrderByCreatedAtDesc(actor.legacyUserId(), false, page)
                : repository.findByUserIdOrderByCreatedAtDesc(actor.legacyUserId(), page);
        if (actor.enforced()) return unread
                ? repository.findByUserPrincipalIdAndIsReadOrderByCreatedAtDesc(actor.principalId(), false, page)
                : repository.findByUserPrincipalIdOrderByCreatedAtDesc(actor.principalId(), page);
        return unread
                ? repository.findUnreadByPrincipalOrUnmigratedLegacyUser(actor.principalId(), actor.legacyUserId(), false, page)
                : repository.findByPrincipalOrUnmigratedLegacyUser(actor.principalId(), actor.legacyUserId(), page);
    }

    public Notification owned(Long id, InboxActor actor) {
        return (actor.legacyOnly() ? repository.findByIdAndUserId(id, actor.legacyUserId())
                : actor.enforced() ? repository.findByIdAndUserPrincipalId(id, actor.principalId())
                : repository.findByIdAndPrincipalOrUnmigratedLegacyUser(id, actor.principalId(), actor.legacyUserId()))
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    public NotificationResponse response(Notification row) { return mapper.toResponse(row); }
    public Boolean isRead(Notification row) { return row.getIsRead(); }
    public void setRead(Notification row, LocalDateTime at) { row.setIsRead(true); row.setReadAt(at); }
    public void save(Notification row) { repository.save(row); }
    public void saveAll(List<Notification> rows) { repository.saveAll(rows); }

    public void fallback(List<Notification> rows, String surface) {
        long count = rows.stream().filter(n -> n.getUserPrincipalId() == null).count();
        if (count > 0) Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "notification").tag("surface", surface).register(metrics).increment(count);
    }

    public long unreadCount(InboxActor actor) {
        if (actor.legacyOnly()) return repository.countByUserIdAndIsRead(actor.legacyUserId(), false);
        return actor.enforced() ? repository.countByUserPrincipalIdAndIsRead(actor.principalId(), false)
                : repository.countByPrincipalOrUnmigratedLegacyUserAndIsRead(actor.principalId(), actor.legacyUserId(), false);
    }

    public void delete(Notification row) { repository.delete(row); }
    public void deleteLegacy(Long id, Long userId) {
        if (repository.deleteByIdAndUserId(id, userId) == 0) throw new NotificationNotFoundException(id);
    }
    public void markLegacy(Long id, Long userId, LocalDateTime at) { repository.markAsRead(id, userId, at); }
    public int markAllLegacy(Long userId, LocalDateTime at) { return repository.markAllAsReadByUser(userId, at); }
}
