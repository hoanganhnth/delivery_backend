package com.delivery.tracking_service.websocket;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShipperLocationWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ShipperLocationWebSocketHandler.class);
    
    // Lưu trữ các session WebSocket kết nối
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    
    // Lưu trữ session theo shipper đang theo dõi
    private final Map<String, Set<String>> shipperSubscriptions = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper;

    public ShipperLocationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        log.info("✅ WebSocket connected: sessionId={}", sessionId);
        
        // Gửi thông báo kết nối thành công
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connection_established",
            "sessionId", sessionId,
            "message", "Connected to shipper location tracking"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        try {
            // Parse message từ client
            @SuppressWarnings("unchecked")
            Map<String, Object> clientMessage = objectMapper.readValue(payload, Map.class);
            String action = (String) clientMessage.get("action");
            
            switch (action) {
                case "subscribe_shipper":
                    handleSubscribeShipper(session, clientMessage);
                    break;
                case "unsubscribe_shipper":
                    handleUnsubscribeShipper(session, clientMessage);
                    break;
                case "subscribe_area":
                    handleSubscribeArea(session, clientMessage);
                    break;
                default:
                    log.warn("⚠️ Unknown action: {} from session: {}", action, sessionId);
            }
            
        } catch (Exception e) {
            log.error("💥 Error processing WebSocket message from session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    private void handleSubscribeShipper(WebSocketSession session, Map<String, Object> message) throws Exception {
        String sessionId = session.getId();
        Object shipperIdObj = message.get("shipperId");
        
        if (shipperIdObj != null) {
            String shipperId = String.valueOf(shipperIdObj);
            
            // Thêm session vào danh sách theo dõi shipper này
            shipperSubscriptions.computeIfAbsent(shipperId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            
            log.info("📍 Session {} subscribed to shipper {}", sessionId, shipperId);
            
            // Gửi phản hồi xác nhận
            Map<String, Object> response = Map.of(
                "type", "subscription_confirmed",
                "shipperId", shipperId,
                "message", "Subscribed to shipper " + shipperId
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    private void handleUnsubscribeShipper(WebSocketSession session, Map<String, Object> message) throws Exception {
        String sessionId = session.getId();
        Object shipperIdObj = message.get("shipperId");
        
        if (shipperIdObj != null) {
            String shipperId = String.valueOf(shipperIdObj);
            
            // Xóa session khỏi danh sách theo dõi shipper này
            Set<String> subscribers = shipperSubscriptions.get(shipperId);
            if (subscribers != null) {
                subscribers.remove(sessionId);
                if (subscribers.isEmpty()) {
                    shipperSubscriptions.remove(shipperId);
                }
            }
            
            log.info("🔄 Session {} unsubscribed from shipper {}", sessionId, shipperId);
            
            // Gửi phản hồi xác nhận
            Map<String, Object> response = Map.of(
                "type", "unsubscription_confirmed",
                "shipperId", shipperId,
                "message", "Unsubscribed from shipper " + shipperId
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    private void handleSubscribeArea(WebSocketSession session, Map<String, Object> message) throws Exception {
        String sessionId = session.getId();
        Double latitude = (Double) message.get("latitude");
        Double longitude = (Double) message.get("longitude");
        Double radius = (Double) message.get("radius");
        
        if (latitude != null && longitude != null && radius != null) {
            // Lưu thông tin area subscription cho session này
            session.getAttributes().put("area_lat", latitude);
            session.getAttributes().put("area_lng", longitude);
            session.getAttributes().put("area_radius", radius);
            
            log.info("🌍 Session {} subscribed to area: ({}, {}) radius {}km", 
                    sessionId, latitude, longitude, radius);
            
            // Gửi phản hồi xác nhận
            Map<String, Object> response = Map.of(
                "type", "area_subscription_confirmed",
                "latitude", latitude,
                "longitude", longitude,
                "radius", radius,
                "message", "Subscribed to area tracking"
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        
        // Xóa tất cả subscription của session này
        shipperSubscriptions.values().forEach(subscribers -> subscribers.remove(sessionId));
        shipperSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        log.info("❌ WebSocket disconnected: sessionId={}, status={}", sessionId, status);
    }

    /**
     * Broadcast vị trí shipper mới tới tất cả client đang theo dõi
     */
    public void broadcastShipperLocation(ShipperLocationResponse location) {
        String shipperId = String.valueOf(location.getShipperId());
        Set<String> subscribers = shipperSubscriptions.get(shipperId);
        
        if (subscribers != null && !subscribers.isEmpty()) {
            try {
                Map<String, Object> locationUpdate = Map.of(
                    "type", "location_update",
                    "shipperId", location.getShipperId(),
                    "latitude", location.getLatitude(),
                    "longitude", location.getLongitude(),
                    "isOnline", location.getIsOnline(),
                    "speed", location.getSpeed() != null ? location.getSpeed() : 0.0,
                    "heading", location.getHeading() != null ? location.getHeading() : 0.0,
                    "timestamp", location.getUpdatedAt()
                );
                
                String message = objectMapper.writeValueAsString(locationUpdate);
                
                // Gửi tới tất cả session đang subscribe shipper này
                subscribers.forEach(sessionId -> {
                    WebSocketSession session = activeSessions.get(sessionId);
                    if (session != null && session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(message));
                        } catch (Exception e) {
                            log.error("💥 Error sending location update to session {}: {}", sessionId, e.getMessage());
                        }
                    }
                });
                
                log.debug("📡 Broadcasted location update for shipper {} to {} subscribers", 
                         shipperId, subscribers.size());
                
            } catch (Exception e) {
                log.error("💥 Error broadcasting shipper location: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Broadcast tới tất cả client đang theo dõi area
     */
    public void broadcastAreaLocationUpdate(ShipperLocationResponse location) {
        if (location.getLatitude() == null || location.getLongitude() == null) {
            return;
        }
        
        try {
            Map<String, Object> locationUpdate = Map.of(
                "type", "area_location_update",
                "shipperId", location.getShipperId(),
                "latitude", location.getLatitude(),
                "longitude", location.getLongitude(),
                "isOnline", location.getIsOnline(),
                "speed", location.getSpeed() != null ? location.getSpeed() : 0.0,
                "heading", location.getHeading() != null ? location.getHeading() : 0.0,
                "timestamp", location.getUpdatedAt()
            );
            
            String message = objectMapper.writeValueAsString(locationUpdate);
            
            // Gửi tới các session có area subscription và shipper nằm trong vùng
            activeSessions.values().forEach(session -> {
                if (session.isOpen() && isLocationInSessionArea(session, location)) {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (Exception e) {
                        log.error("💥 Error sending area location update to session {}: {}", 
                                session.getId(), e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            log.error("💥 Error broadcasting area location update: {}", e.getMessage(), e);
        }
    }

    private boolean isLocationInSessionArea(WebSocketSession session, ShipperLocationResponse location) {
        Map<String, Object> attributes = session.getAttributes();
        
        Double areaLat = (Double) attributes.get("area_lat");
        Double areaLng = (Double) attributes.get("area_lng");
        Double areaRadius = (Double) attributes.get("area_radius");
        
        if (areaLat != null && areaLng != null && areaRadius != null) {
            // Tính khoảng cách đơn giản (có thể dùng formula chính xác hơn)
            double distance = calculateDistance(areaLat, areaLng, location.getLatitude(), location.getLongitude());
            return distance <= areaRadius;
        }
        
        return false;
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        // Haversine formula để tính khoảng cách
        final int R = 6371; // Radius của trái đất (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}
