package com.delivery.tracking_service.websocket;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bounded asynchronous fan-out. For each session/delivery it coalesces updates
 * with the same online state while retaining the latest state transition and
 * latest location. Slow clients therefore cannot block the publisher thread or
 * grow an unbounded queue.
 */
@Component
public class LocationMessageDispatcher {

    private final Executor executor;
    private final ThreadPoolExecutor ownedExecutor;
    private final Map<String, PendingSession> pending = new ConcurrentHashMap<>();
    private final LongAdder offered = new LongAdder();
    private final LongAdder sent = new LongAdder();
    private final LongAdder coalesced = new LongAdder();
    private final LongAdder failed = new LongAdder();

    @Autowired
    public LocationMessageDispatcher(
            @Value("${app.websocket.fanout.workers:4}") int workers,
            @Value("${app.websocket.fanout.executor-queue-capacity:1024}") int queueCapacity) {
        int boundedWorkers = Math.max(1, Math.min(workers, 32));
        int boundedQueue = Math.max(64, Math.min(queueCapacity, 100_000));
        this.ownedExecutor = new ThreadPoolExecutor(
                boundedWorkers, boundedWorkers, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(boundedQueue),
                runnable -> {
                    Thread thread = new Thread(runnable, "tracking-location-fanout");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.executor = ownedExecutor;
    }

    LocationMessageDispatcher(Executor executor) {
        this.executor = executor;
        this.ownedExecutor = null;
    }

    public void dispatch(WebSocketSession session, long deliveryId,
                         TextMessage message, boolean online) {
        offered.increment();
        PendingSession sessionQueue = pending.computeIfAbsent(
                session.getId(), ignored -> new PendingSession());
        boolean schedule;
        synchronized (sessionQueue) {
            ArrayDeque<PendingMessage> roomQueue = sessionQueue.byDelivery
                    .computeIfAbsent(deliveryId, ignored -> new ArrayDeque<>(2));
            PendingMessage last = roomQueue.peekLast();
            if (last != null && last.online() == online) {
                roomQueue.removeLast();
                coalesced.increment();
            }
            roomQueue.addLast(new PendingMessage(message, online));
            while (roomQueue.size() > 2) {
                roomQueue.removeFirst();
                coalesced.increment();
            }
            schedule = !sessionQueue.draining;
            if (schedule) sessionQueue.draining = true;
        }
        if (schedule) schedule(session, sessionQueue);
    }

    public void sendControl(WebSocketSession session, TextMessage message) throws Exception {
        synchronized (session) {
            session.sendMessage(message);
        }
    }

    public void remove(String sessionId) {
        pending.remove(sessionId);
    }

    public Stats stats() {
        return new Stats(offered.sum(), sent.sum(), coalesced.sum(), failed.sum());
    }

    private void schedule(WebSocketSession session, PendingSession sessionQueue) {
        try {
            executor.execute(() -> drain(session, sessionQueue));
        } catch (RuntimeException rejected) {
            synchronized (sessionQueue) {
                sessionQueue.draining = false;
            }
            failed.increment();
        }
    }

    private void drain(WebSocketSession session, PendingSession sessionQueue) {
        while (session.isOpen()) {
            PendingMessage next;
            synchronized (sessionQueue) {
                next = poll(sessionQueue);
                if (next == null) {
                    sessionQueue.draining = false;
                    if (sessionQueue.byDelivery.isEmpty()) pending.remove(session.getId(), sessionQueue);
                    return;
                }
            }
            try {
                synchronized (session) {
                    session.sendMessage(next.message());
                }
                sent.increment();
            } catch (Exception exception) {
                failed.increment();
                pending.remove(session.getId(), sessionQueue);
                return;
            }
        }
        pending.remove(session.getId(), sessionQueue);
    }

    private PendingMessage poll(PendingSession sessionQueue) {
        var iterator = sessionQueue.byDelivery.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ArrayDeque<PendingMessage>> entry = iterator.next();
            PendingMessage message = entry.getValue().pollFirst();
            if (entry.getValue().isEmpty()) iterator.remove();
            if (message != null) return message;
        }
        return null;
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }

    private static final class PendingSession {
        private final Map<Long, ArrayDeque<PendingMessage>> byDelivery = new LinkedHashMap<>();
        private boolean draining;
    }

    private record PendingMessage(TextMessage message, boolean online) {}

    public record Stats(long offered, long sent, long coalesced, long failed) {}
}
