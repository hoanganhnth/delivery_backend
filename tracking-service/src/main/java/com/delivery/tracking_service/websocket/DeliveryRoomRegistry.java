package com.delivery.tracking_service.websocket;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Local authorized membership index for legacy single and projected batch rooms. */
@Component
public class DeliveryRoomRegistry implements com.delivery.tracking.application.api.DeliveryRoomIndexPort {

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> activeDeliveriesByShipper = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> roomsBySession = new ConcurrentHashMap<>();

    public synchronized void subscribe(long deliveryId, long shipperId, String sessionId) {
        if (!activeDeliveries(shipperId).contains(deliveryId)) activate(deliveryId, shipperId);
        Room room = rooms.computeIfAbsent(deliveryId, ignored -> new Room(shipperId));
        if (room.shipperId() != shipperId) {
            throw new IllegalStateException("Delivery room shipper identity changed");
        }
        room.sessions().add(sessionId);
        roomsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
                .add(deliveryId);
    }

    public synchronized void activate(long deliveryId, long shipperId) {
        synchronize(shipperId, Set.of(deliveryId));
    }

    public synchronized void synchronize(long shipperId, Set<Long> deliveryIds) {
        Set<Long> incoming = Set.copyOf(deliveryIds);
        Set<Long> previous = activeDeliveriesByShipper.getOrDefault(shipperId, Set.of());
        if (incoming.isEmpty()) activeDeliveriesByShipper.remove(shipperId);
        else activeDeliveriesByShipper.put(shipperId, incoming);
        for (Long deliveryId : previous) if (!incoming.contains(deliveryId)) removeRoom(deliveryId);
    }

    public synchronized void end(long deliveryId, long shipperId) {
        Set<Long> current = activeDeliveries(shipperId);
        if (current.contains(deliveryId)) {
            var remaining = new java.util.HashSet<>(current); remaining.remove(deliveryId);
            synchronize(shipperId, remaining);
        }
    }

    public synchronized void unsubscribe(String sessionId, long shipperId) {
        Set<Long> sessionRooms = roomsBySession.get(sessionId);
        if (sessionRooms == null) return;
        for (Long deliveryId : new ArrayList<>(sessionRooms)) {
            Room room = rooms.get(deliveryId);
            if (room != null && room.shipperId() == shipperId) {
                removeMembership(deliveryId, sessionId);
            }
        }
    }

    public synchronized void removeSession(String sessionId) {
        Set<Long> sessionRooms = roomsBySession.remove(sessionId);
        if (sessionRooms == null) return;
        for (Long deliveryId : sessionRooms) {
            Room room = rooms.get(deliveryId);
            if (room != null) {
                room.sessions().remove(sessionId);
                if (room.sessions().isEmpty()
                        && !activeDeliveries(room.shipperId()).contains(deliveryId)) {
                    rooms.remove(deliveryId, room);
                }
            }
        }
    }

    public List<String> subscribersForShipper(long shipperId) {
        var sessions = new java.util.HashSet<String>();
        for (Long deliveryId : activeDeliveries(shipperId)) sessions.addAll(subscribers(deliveryId, shipperId));
        return List.copyOf(sessions);
    }

    public List<String> subscribers(long deliveryId, long shipperId) {
        Room room = rooms.get(deliveryId);
        if (room == null || room.shipperId() != shipperId
                || !activeDeliveries(shipperId).contains(deliveryId)) {
            return List.of();
        }
        return List.copyOf(room.sessions());
    }

    public Long activeDelivery(long shipperId) {
        return activeDeliveries(shipperId).stream().min(Long::compareTo).orElse(null);
    }

    public Set<Long> activeDeliveries(long shipperId) {
        return activeDeliveriesByShipper.getOrDefault(shipperId, Set.of());
    }

    public int roomCount() {
        return rooms.size();
    }

    private void removeMembership(Long deliveryId, String sessionId) {
        Room room = rooms.get(deliveryId);
        if (room != null) room.sessions().remove(sessionId);
        Set<Long> sessionRooms = roomsBySession.get(sessionId);
        if (sessionRooms != null) {
            sessionRooms.remove(deliveryId);
            if (sessionRooms.isEmpty()) roomsBySession.remove(sessionId, sessionRooms);
        }
    }

    private void removeRoom(Long deliveryId) {
        Room removed = rooms.remove(deliveryId);
        if (removed == null) return;
        for (String sessionId : removed.sessions()) {
            Set<Long> sessionRooms = roomsBySession.get(sessionId);
            if (sessionRooms != null) {
                sessionRooms.remove(deliveryId);
                if (sessionRooms.isEmpty()) roomsBySession.remove(sessionId, sessionRooms);
            }
        }
    }

    private record Room(long shipperId, Set<String> sessions) {
        private Room(long shipperId) {
            this(shipperId, ConcurrentHashMap.newKeySet());
        }
    }
}
