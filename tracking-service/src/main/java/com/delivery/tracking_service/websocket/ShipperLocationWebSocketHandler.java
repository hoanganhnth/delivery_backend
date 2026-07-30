package com.delivery.tracking_service.websocket;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.service.ShipperLocationEventPublisher;
import com.delivery.tracking_service.service.DeliveryTrackingAccessClient;
import com.delivery.tracking_service.service.ShipperPublisherSessionManager;
import com.delivery.tracking_service.service.LocationFanoutPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;

@Component
public class ShipperLocationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipperLocationWebSocketHandler.class);

    // Lưu trữ các session WebSocket kết nối
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    private final Map<String, PublisherLease> publisherLeases = new ConcurrentHashMap<>();
    private final Map<Long, String> localPublisherSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final RedisGeoRepository redisGeoRepository;
    private final ShipperLocationEventPublisher eventPublisher;
    private final DeliveryTrackingAccessClient trackingAccessClient;
    private final ShipperPublisherSessionManager publisherSessionManager;
    private final DeliveryRoomRegistry deliveryRooms;
    private final LocationMessageDispatcher messageDispatcher;
    private final LocationFanoutPublisher locationFanoutPublisher;

    @Autowired
    public ShipperLocationWebSocketHandler(ObjectMapper objectMapper, 
                                           RedisGeoRepository redisGeoRepository,
                                           ShipperLocationEventPublisher eventPublisher,
                                           DeliveryTrackingAccessClient trackingAccessClient,
                                           ShipperPublisherSessionManager publisherSessionManager,
                                           DeliveryRoomRegistry deliveryRooms,
                                           LocationMessageDispatcher messageDispatcher,
                                           LocationFanoutPublisher locationFanoutPublisher) {
        this.objectMapper = objectMapper;
        this.redisGeoRepository = redisGeoRepository;
        this.eventPublisher = eventPublisher;
        this.trackingAccessClient = trackingAccessClient;
        this.publisherSessionManager = publisherSessionManager;
        this.deliveryRooms = deliveryRooms;
        this.messageDispatcher = messageDispatcher;
        this.locationFanoutPublisher = locationFanoutPublisher;
    }

    /** Compatibility constructor retained for focused tests. */
    public ShipperLocationWebSocketHandler(ObjectMapper objectMapper,
                                           RedisGeoRepository redisGeoRepository,
                                           ShipperLocationEventPublisher eventPublisher,
                                           DeliveryTrackingAccessClient trackingAccessClient,
                                           ShipperPublisherSessionManager publisherSessionManager) {
        this(objectMapper, redisGeoRepository, eventPublisher, trackingAccessClient,
                publisherSessionManager, new DeliveryRoomRegistry(),
                new LocationMessageDispatcher(Runnable::run), null);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (authenticatedUserId(session) == null || authenticatedRole(session) == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required"));
            return;
        }
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        Long userId = authenticatedUserId(session);
        PublisherLease lease = null;
        if ("SHIPPER".equals(authenticatedRole(session))) {
            try {
                lease = publisherSessionManager.acquire(userId, sessionId);
                publisherLeases.put(sessionId, lease);
                String previousSessionId = localPublisherSessions.put(userId, sessionId);
                if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
                    supersedeLocalPublisher(previousSessionId);
                }
            } catch (Exception exception) {
                activeSessions.remove(sessionId);
                log.error("Cannot acquire publisher generation for shipper {}", userId, exception);
                session.close(CloseStatus.SERVER_ERROR.withReason("Publisher lease unavailable"));
                return;
            }
        }
        log.info("✅ WebSocket connected: sessionId={}", sessionId);

        // Gửi thông báo kết nối thành công
        Map<String, Object> welcomeMessage = new java.util.HashMap<>();
        welcomeMessage.put("type", "connection_established");
        welcomeMessage.put("sessionId", sessionId);
        welcomeMessage.put("message", "Connected to shipper location tracking");
        if (lease != null) {
            welcomeMessage.put("publisherGeneration", lease.generation());
        }

        send(session, welcomeMessage);
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

            if (action == null) {
                log.warn("⚠️ Received WebSocket message without 'action' field from session {}", sessionId);
                return;
            }

            switch (action) {
                case "subscribe_shipper":
                    handleSubscribeShipper(session, clientMessage);
                    break;
                case "unsubscribe_shipper":
                    handleUnsubscribeShipper(session, clientMessage);
                    break;
                case "subscribe_area":
                    sendError(session, "FORBIDDEN", "Area tracking is not available in MVP");
                    break;
                case "update_location":
                    if ("SHIPPER".equals(authenticatedRole(session))) {
                        handleUpdateLocation(session, clientMessage);
                    } else {
                        sendError(session, "FORBIDDEN", "Only shippers can update location");
                    }
                    break;
                case "ping":
                    if ("SHIPPER".equals(authenticatedRole(session))
                            && !ensureCurrentPublisher(session)) {
                        return;
                    }
                    // Respond to heartbeat ping from clients
                    Map<String, Object> pongResponse = Map.of(
                            "type", "pong",
                            "timestamp", java.time.Instant.now().toString());
                    send(session, pongResponse);
                    break;
                default:
                    log.warn("⚠️ Unknown action: {} from session: {}", action, sessionId);
            }

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid WebSocket message from session {}: {}", sessionId, e.getMessage());
            if (session.isOpen()) {
                sendError(session, "MESSAGE_PROCESSING_FAILED", "Unable to process message");
            }
        } catch (Exception e) {
            log.error("💥 Error processing WebSocket message from session {}: {}", sessionId, e.getMessage(), e);
            if (session.isOpen()) {
                sendError(session, "MESSAGE_PROCESSING_FAILED", "Unable to process message");
            }
        }
    }

    private void handleUpdateLocation(WebSocketSession session, Map<String, Object> message) throws Exception {
        if (!ensureCurrentPublisher(session)) {
            return;
        }
        Long shipperId = authenticatedUserId(session);
        Double latitude = requiredFiniteNumberInRange(message, "latitude", -90.0, 90.0);
        Double longitude = requiredFiniteNumberInRange(message, "longitude", -180.0, 180.0);

        ShipperLocationResponse response = new ShipperLocationResponse();
        response.setShipperId(shipperId);
        response.setLatitude(latitude);
        response.setLongitude(longitude);
        response.setAccuracy(optionalFiniteNumber(message, "accuracy"));
        response.setSpeed(optionalFiniteNumber(message, "speed"));
        response.setHeading(optionalFiniteNumber(message, "heading"));
        response.setIsOnline(optionalBoolean(message, "isOnline", true));
        String serverTimestamp = LocalDateTime.now().toString();
        response.setUpdatedAt(serverTimestamp);
        response.setLastPing(serverTimestamp);

        // Lưu vào Redis
        redisGeoRepository.cacheShipperLocation(shipperId, response);

        // Publish qua Kafka cho Match Service
        eventPublisher.publishLocationUpdate(response, "WEBSOCKET");
        log.debug("📤 [WS] Published location to Kafka `shipper.location-updated` for shipper {}", shipperId);

        // Broadcast tới subscribers
        fanout(response);

        log.info("📍 [WS] Updated location for shipper {} and queued authorized fanout",
                shipperId);
    }

    private void handleSubscribeShipper(WebSocketSession session, Map<String, Object> message) throws Exception {
        String sessionId = session.getId();
        Long shipperIdValue = requiredLong(message, "shipperId");
        Long deliveryId = requiredLong(message, "deliveryId");
        Long userId = authenticatedUserId(session);
        String role = authenticatedRole(session);
        boolean allowed;
        try {
            allowed = trackingAccessClient.canTrack(deliveryId, userId, role, shipperIdValue);
        } catch (Exception e) {
            log.error("Tracking authorization unavailable for delivery {}", deliveryId, e);
            sendError(session, "AUTHORIZATION_UNAVAILABLE", "Cannot verify delivery participant");
            return;
        }
        if (!allowed) {
            sendError(session, "FORBIDDEN", "Not a participant of this active delivery");
            return;
        }
        String shipperId = shipperIdValue.toString();
        deliveryRooms.subscribe(deliveryId, shipperIdValue, sessionId);

        log.info("📍 Session {} subscribed to shipper {}", sessionId, shipperId);

            // Gửi phản hồi xác nhận
        Map<String, Object> response = Map.of(
                "type", "subscription_confirmed",
                "shipperId", shipperId,
                "message", "Subscribed to shipper " + shipperId);

        send(session, response);

        // A coalesced/broker message may have arrived before this subscriber.
        // Send the Redis realtime source immediately so the last location is not lost.
        ShipperLocationResponse latest = redisGeoRepository.getCachedShipperLocation(shipperIdValue);
        if (latest != null) {
            dispatchLocation(session, deliveryId, latest);
        }
    }

    private void handleUnsubscribeShipper(WebSocketSession session, Map<String, Object> message) throws Exception {
        String sessionId = session.getId();
        Object shipperIdObj = message.get("shipperId");

        if (shipperIdObj != null) {
            Long shipperIdValue = requiredLong(message, "shipperId");
            String shipperId = shipperIdValue.toString();
            deliveryRooms.unsubscribe(sessionId, shipperIdValue);

            log.info("🔄 Session {} unsubscribed from shipper {}", sessionId, shipperId);

            // Gửi phản hồi xác nhận
            Map<String, Object> response = Map.of(
                    "type", "unsubscription_confirmed",
                    "shipperId", shipperId,
                    "message", "Unsubscribed from shipper " + shipperId);

            send(session, response);
        }
    }

    private Long authenticatedUserId(WebSocketSession session) {
        Object value = session.getAttributes().get("authenticatedUserId");
        return value instanceof Number number ? number.longValue() : null;
    }

    private String authenticatedRole(WebSocketSession session) {
        Object value = session.getAttributes().get("authenticatedRole");
        return value instanceof String role ? role : null;
    }

    private Double requiredNumber(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        return number.doubleValue();
    }

    private Double requiredFiniteNumberInRange(Map<String, Object> message, String field, double min, double max) {
        Double value = requiredNumber(message, field);
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private Long requiredLong(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        long parsed = number.longValue();
        if (parsed <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return parsed;
    }

    private Double optionalFiniteNumber(Map<String, Object> message, String field) {
        Object value = message.get(field);
        if (value == null) {
            return null;
        }
        Double parsed = requiredNumber(message, field);
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return parsed;
    }

    private Boolean optionalBoolean(Map<String, Object> message, String field, boolean defaultValue) {
        if (!message.containsKey(field)) {
            return defaultValue;
        }
        Object value = message.get(field);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException(field + " must be boolean");
    }

    private void sendError(WebSocketSession session, String code, String message) throws Exception {
        send(session, Map.of("type", "error", "code", code, "message", message));
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws Exception {
        messageDispatcher.sendControl(
                session, new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private boolean ensureCurrentPublisher(WebSocketSession session) throws Exception {
        PublisherLease lease = publisherLeases.get(session.getId());
        if (lease != null && publisherSessionManager.refreshIfCurrent(lease)) {
            return true;
        }
        sendError(session, "PUBLISHER_SUPERSEDED", "A newer shipper location session is active");
        session.close(CloseStatus.POLICY_VIOLATION.withReason("Publisher superseded"));
        return false;
    }

    private void supersedeLocalPublisher(String previousSessionId) {
        WebSocketSession previous = activeSessions.get(previousSessionId);
        if (previous == null || !previous.isOpen()) {
            return;
        }
        try {
            sendError(previous, "PUBLISHER_SUPERSEDED", "A newer shipper location session is active");
        } catch (Exception exception) {
            log.debug("Cannot notify superseded publisher session {}", previousSessionId, exception);
        }
        try {
            previous.close(CloseStatus.POLICY_VIOLATION.withReason("Publisher superseded"));
        } catch (Exception exception) {
            log.debug("Cannot close superseded publisher session {}", previousSessionId, exception);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        PublisherLease lease = publisherLeases.remove(sessionId);
        if (lease != null) {
            localPublisherSessions.remove(lease.shipperId(), sessionId);
            publisherSessionManager.disconnected(lease, offline -> {
                fanout(offline);
            });
        }

        deliveryRooms.removeSession(sessionId);
        messageDispatcher.remove(sessionId);

        log.info("❌ WebSocket disconnected: sessionId={}, status={}", sessionId, status);
    }

    /**
     * Broadcast vị trí shipper mới tới tất cả client đang theo dõi
     */
    public void broadcastShipperLocation(ShipperLocationResponse location) {
        Long shipperId = location == null ? null : location.getShipperId();
        Long deliveryId = shipperId == null ? null : deliveryRooms.activeDelivery(shipperId);
        if (deliveryId != null) broadcastDeliveryLocation(deliveryId, location);
    }

    public void broadcastDeliveryLocation(Long deliveryId, ShipperLocationResponse location) {
        Long shipperId = location == null ? null : location.getShipperId();
        var subscribers = deliveryId == null || shipperId == null ? java.util.List.<String>of()
                : deliveryRooms.subscribers(deliveryId, shipperId);

        if (deliveryId != null && !subscribers.isEmpty()) {
            try {
                subscribers.forEach(sessionId -> {
                    WebSocketSession session = activeSessions.get(sessionId);
                    if (session != null && session.isOpen()) {
                        try {
                            dispatchLocation(session, deliveryId, location);
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

    private void fanout(ShipperLocationResponse location) {
        if (locationFanoutPublisher == null) broadcastShipperLocation(location);
        else locationFanoutPublisher.publish(location);
    }

    private void dispatchLocation(WebSocketSession session, long deliveryId,
                                  ShipperLocationResponse location) throws Exception {
        Map<String, Object> locationUpdate = new java.util.HashMap<>();
        locationUpdate.put("type", "location_update");
        locationUpdate.put("shipperId", location.getShipperId());
        locationUpdate.put("latitude", location.getLatitude());
        locationUpdate.put("longitude", location.getLongitude());
        locationUpdate.put("isOnline", location.getIsOnline() != null ? location.getIsOnline() : false);
        locationUpdate.put("accuracy", location.getAccuracy());
        locationUpdate.put("speed", location.getSpeed());
        locationUpdate.put("heading", location.getHeading());
        locationUpdate.put("timestamp", location.getUpdatedAt());
        messageDispatcher.dispatch(session, deliveryId,
                new TextMessage(objectMapper.writeValueAsString(locationUpdate)),
                Boolean.TRUE.equals(location.getIsOnline()));
    }

}
