package com.delivery.tracking_service.websocket;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local authorized room membership. A shipper can be routed to only one active
 * delivery room; activating a newer authorized assignment evicts the previous
 * room so participants of a completed delivery cannot receive later work.
 */
@Component
public class DeliveryRoomRegistry {

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Long> activeDeliveryByShipper = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> roomsBySession = new ConcurrentHashMap<>();

    public synchronized void subscribe(long deliveryId, long shipperId, String sessionId) {
        activate(deliveryId, shipperId);
        Room room = rooms.computeIfAbsent(deliveryId, ignored -> new Room(shipperId));
        if (room.shipperId() != shipperId) {
            throw new IllegalStateException("Delivery room shipper identity changed");
        }
        room.sessions().add(sessionId);
        roomsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
                .add(deliveryId);
    }

    public synchronized void activate(long deliveryId, long shipperId) {
        Long previousDelivery = activeDeliveryByShipper.put(shipperId, deliveryId);
        if (previousDelivery != null && previousDelivery != deliveryId) {
            removeRoom(previousDelivery);
        }
    }

    public synchronized void end(long deliveryId, long shipperId) {
        if (activeDeliveryByShipper.remove(shipperId, deliveryId)) {
            removeRoom(deliveryId);
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
                        && !activeDeliveryByShipper.containsValue(deliveryId)) {
                    rooms.remove(deliveryId, room);
                }
            }
        }
    }

    public List<String> subscribersForShipper(long shipperId) {
        Long deliveryId = activeDeliveryByShipper.get(shipperId);
        if (deliveryId == null) return List.of();
        Room room = rooms.get(deliveryId);
        if (room == null || room.shipperId() != shipperId) return List.of();
        return List.copyOf(room.sessions());
    }

    public List<String> subscribers(long deliveryId, long shipperId) {
        Room room = rooms.get(deliveryId);
        if (room == null || room.shipperId() != shipperId
                || !Long.valueOf(deliveryId).equals(activeDeliveryByShipper.get(shipperId))) {
            return List.of();
        }
        return List.copyOf(room.sessions());
    }

    public Long activeDelivery(long shipperId) {
        return activeDeliveryByShipper.get(shipperId);
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
