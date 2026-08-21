package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SimulationService {

    private final ObjectMapper objectMapper;
    private final SimulatorProperties properties;
    private final GatewayClient gateway;
    private final Map<String, SimulationRunState> runs = new ConcurrentHashMap<>();
    private final Map<String, PendingAlgorithmTrace> pendingAlgorithmTraces = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    private static final Duration PENDING_TRACE_RETENTION = Duration.ofMinutes(20);
    private static final int MAX_PENDING_ALGORITHM_TRACES = 2048;

    public SimulationService(ObjectMapper objectMapper,
                             SimulatorProperties properties,
                             GatewayClient gateway) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.gateway = gateway;
        this.executor = Executors.newFixedThreadPool(properties.getMaxConcurrentRuns(), runnable -> {
            Thread thread = new Thread(runnable, "simulator-runner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<String, Object> validate(JsonNode scenario) {
        ensureEnabled();
        validateTarget();
        List<String> errors = validationErrors(scenario);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("gatewayBaseUrl", properties.getGatewayBaseUrl());
        result.put("maxShippers", properties.getMaxShippers());
        return result;
    }

    public Map<String, Object> start(JsonNode scenario) {
        ensureEnabled();
        validateTarget();
        List<String> errors = validationErrors(scenario);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        SimulationRunState state = new SimulationRunState(objectMapper, scenario);
        runs.put(state.getRunId(), state);
        executor.submit(() -> execute(state));
        return state.snapshot();
    }

    public Map<String, Object> snapshot(String runId) {
        SimulationRunState state = requireRun(runId);
        attachPendingAlgorithmTraces(state);
        return state.snapshot();
    }

    /** Attach a read-only Match decision trace to the matching active run. */
    public void recordAlgorithmTrace(JsonNode trace) {
        if (trace == null || !trace.isObject() || !trace.path("eventId").isTextual()) {
            return;
        }
        prunePendingAlgorithmTraces();
        for (SimulationRunState state : runs.values()) {
            if (state.matchesAlgorithmTrace(trace)) {
                state.addAlgorithmTrace(trace);
                return;
            }
        }
        // Kafka can deliver the trace between Match's result publication and
        // the runner's next order/delivery poll. Keep it briefly instead of
        // dropping an otherwise valid explanation that can be correlated once
        // the run learns its IDs.
        bufferPendingAlgorithmTrace(trace);
    }

    public SseEmitter stream(String runId) {
        SimulationRunState state = requireRun(runId);
        attachPendingAlgorithmTraces(state);
        SseEmitter emitter = new SseEmitter(0L);
        state.addEmitter(emitter);
        return emitter;
    }

    public Map<String, Object> pause(String runId) {
        SimulationRunState state = requireRun(runId);
        if (!state.isTerminal()) {
            state.pause();
            state.addEvent("RUNNER", "Scenario tạm dừng", "Các actor ảo dừng action tiếp theo", "WARNING");
        }
        return state.snapshot();
    }

    public Map<String, Object> resume(String runId) {
        SimulationRunState state = requireRun(runId);
        if (!state.isTerminal()) {
            state.resume();
            state.addEvent("RUNNER", "Scenario tiếp tục", "Runner tiếp tục xử lý các action còn lại", "INFO");
        }
        return state.snapshot();
    }

    public Map<String, Object> abort(String runId) {
        SimulationRunState state = requireRun(runId);
        if (!state.isTerminal()) {
            state.abort();
            state.addEvent("RUNNER", "Scenario bị huỷ", "Không gửi thêm business action; cleanup vẫn cần được kiểm tra", "ERROR");
        }
        return state.snapshot();
    }

    public Map<String, Object> cleanup(String runId) {
        SimulationRunState state = requireRun(runId);
        if (!state.isTerminal()) {
            throw new IllegalStateException("Chỉ được cleanup run đã terminal hoặc đã abort");
        }
        state.completeEmitters();
        runs.remove(runId);
        return Map.of("runId", runId, "cleaned", true);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void execute(SimulationRunState state) {
        try {
            state.setStatus("PROVISIONING");
            state.addEvent("RUNNER", "Khởi tạo Scenario Run", "Run " + state.getRunId() + " chạy qua Gateway thật", "INFO");
            seedLocations(state);

            JsonNode scenario = state.getRawScenario();
            String customerToken = requiredToken(scenario.path("customer"), "token", "customer");
            JsonNode restaurant = scenario.path("restaurant");
            String ownerToken = text(restaurant, "ownerToken", "");
            String mode = text(scenario, "orderMode", "SIMULATED_ORDER");

            if ("HUMAN_ORDER".equals(mode)) {
                state.setStatus("WAITING_FOR_ORDER");
                state.addEvent("RUNNER", "Đang chờ đơn thật từ Delivery App", "Runner poll GET /api/orders/my-orders; không tự sinh order", "WARNING");
                waitForHumanOrder(state, customerToken);
            } else {
                state.setStatus("RUNNING");
                createOrder(state, customerToken);
            }

            if (state.isAborted()) return;
            pollState(state, customerToken);
            fireTriggers(state, "PENDING", customerToken, ownerToken);
            // Cancellation/rejection is usually propagated asynchronously. Do
            // not continue with the next irreversible actor action using the
            // pre-trigger snapshot (for example, auto-confirming an order the
            // customer or restaurant has just terminated).
            pollState(state, customerToken);
            if (isTerminalOrder(state.getOrderStatus())) {
                finishAssertions(state);
                return;
            }

            boolean autoConfirm = restaurant.path("autoConfirm").asBoolean(false);
            boolean pendingTriggerFired = state.isTriggerFiredAtStage("PENDING");
            if ("PENDING".equals(state.getOrderStatus()) && autoConfirm
                    && !ownerToken.isBlank() && !pendingTriggerFired) {
                confirmRestaurant(state, ownerToken);
                pollState(state, customerToken);
            } else {
                state.addEvent("RUNNER", "Không tự xác nhận nhà hàng",
                        pendingTriggerFired
                                ? "Đã có trigger tại PENDING; runner chờ backend hội tụ trước khi thực hiện action tiếp theo"
                                : "autoConfirm=false, order đã qua PENDING hoặc chưa cấu hình ownerToken",
                        "WARNING");
            }

            runDeliveryLoop(state, customerToken, ownerToken);
            finishAssertions(state);
        } catch (RunAbortedException ignored) {
            state.abort();
        } catch (Exception exception) {
            state.setStatus("FAILED");
            state.addEvent("RUNNER", "Scenario thất bại", safeError(exception), "ERROR");
        } finally {
            if (state.isAborted()) {
                state.setStatus("ABORTED");
            }
            state.completeEmitters();
        }
    }

    private void attachPendingAlgorithmTraces(SimulationRunState state) {
        prunePendingAlgorithmTraces();
        for (Map.Entry<String, PendingAlgorithmTrace> entry : pendingAlgorithmTraces.entrySet()) {
            PendingAlgorithmTrace pending = entry.getValue();
            if (state.matchesAlgorithmTrace(pending.trace())) {
                state.addAlgorithmTrace(pending.trace());
                pendingAlgorithmTraces.remove(entry.getKey(), pending);
            }
        }
    }

    private void prunePendingAlgorithmTraces() {
        Instant cutoff = Instant.now().minus(PENDING_TRACE_RETENTION);
        pendingAlgorithmTraces.entrySet().removeIf(entry -> entry.getValue().receivedAt().isBefore(cutoff));
    }

    private void bufferPendingAlgorithmTrace(JsonNode trace) {
        String eventId = trace.path("eventId").asText();
        while (pendingAlgorithmTraces.size() >= MAX_PENDING_ALGORITHM_TRACES) {
            pendingAlgorithmTraces.entrySet().stream()
                    .min(Map.Entry.comparingByValue(java.util.Comparator.comparing(
                            PendingAlgorithmTrace::receivedAt)))
                    .map(Map.Entry::getKey)
                    .ifPresent(pendingAlgorithmTraces::remove);
        }
        pendingAlgorithmTraces.put(eventId,
                new PendingAlgorithmTrace(trace.deepCopy(), Instant.now()));
    }

    private record PendingAlgorithmTrace(JsonNode trace, Instant receivedAt) {
    }

    private void seedLocations(SimulationRunState state) {
        JsonNode shippers = state.getRawScenario().path("shippers");
        for (JsonNode shipper : shippers) {
            checkControl(state);
            String id = text(shipper, "id", "unknown");
            String token = requiredToken(shipper, "token", "shipper " + id);
            boolean online = shipper.path("isOnline").asBoolean(false);
            updateLocation(state, id, token,
                    number(shipper, "initialLat", 0), number(shipper, "initialLng", 0), online);
        }
    }

    private void createOrder(SimulationRunState state, String customerToken) {
        JsonNode scenario = state.getRawScenario();
        JsonNode restaurant = scenario.path("restaurant");
        JsonNode customer = scenario.path("customer");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("restaurantId", restaurant.path("id").asLong());
        request.put("deliveryAddress", text(customer, "deliveryAddress", "Test delivery address"));
        request.put("deliveryLat", number(customer, "lat", 10.776));
        request.put("deliveryLng", number(customer, "lng", 106.7));
        request.put("customerName", text(customer, "name", "Scenario customer"));
        request.put("customerPhone", text(customer, "phone", "0900000000"));
        request.put("paymentMethod", "COD");
        ArrayNode items = request.putArray("items");
        ObjectNode item = items.addObject();
        item.put("menuItemId", restaurant.path("menuItemId").asLong());
        item.put("quantity", Math.max(1, customer.path("itemQuantity").asInt(1)));

        // Use the same server-issued quote path as the real customer app. It
        // keeps the runner compatible with both the current compatibility
        // mode and an isolated environment where quote enforcement is enabled.
        ObjectNode preview = objectMapper.createObjectNode();
        preview.put("restaurantId", restaurant.path("id").asLong());
        preview.put("deliveryLat", number(customer, "lat", 10.776));
        preview.put("deliveryLng", number(customer, "lng", 106.7));
        ArrayNode previewItems = preview.putArray("items");
        ObjectNode previewItem = previewItems.addObject();
        previewItem.put("menuItemId", restaurant.path("menuItemId").asLong());
        previewItem.put("quantity", Math.max(1, customer.path("itemQuantity").asInt(1)));
        state.addEvent("GATEWAY", "Khách lấy checkout quote", "POST /api/orders/checkout-preview", "INFO");
        JsonNode quoteResponse = data(gateway.post("/api/orders/checkout-preview", customerToken,
                preview, state.getCorrelationId()));
        String quoteId = text(quoteResponse, "quoteId", "");
        Map<String, String> createHeaders = new LinkedHashMap<>();
        if (!quoteId.isBlank()) {
            request.put("quoteId", quoteId);
            createHeaders.put("Idempotency-Key", UUID.randomUUID().toString());
        }
        state.addEvent("GATEWAY", "Khách tạo đơn COD", "POST /api/orders", "INFO");
        JsonNode orderResponse = data(gateway.postWithHeaders("/api/orders", customerToken, request,
                state.getCorrelationId(), createHeaders));
        long orderId = positiveId(orderResponse, "id", "order");
        state.setOrder(orderId, text(orderResponse, "status", "PENDING"));
        state.addEvent("ORDER_SERVICE", "Order thật được tạo #" + orderId,
                "Order ID lấy từ response của Gateway", "SUCCESS");
    }

    private void waitForHumanOrder(SimulationRunState state, String customerToken) {
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.getHumanOrderTimeoutSeconds()).toNanos();
        Set<Long> baseline = new HashSet<>();
        JsonNode initial = data(gateway.get("/api/orders/my-orders?page=0&size=100", customerToken, state.getCorrelationId()));
        for (JsonNode order : array(initial, "items")) {
            if (order.hasNonNull("id")) baseline.add(order.path("id").asLong());
        }

        while (System.nanoTime() < deadline) {
            checkControl(state);
            JsonNode page = data(gateway.get("/api/orders/my-orders?page=0&size=100", customerToken, state.getCorrelationId()));
            for (JsonNode order : array(page, "items")) {
                long orderId = order.path("id").asLong(-1);
                long restaurantId = state.getRawScenario().path("restaurant").path("id").asLong(-1);
                if (orderId > 0 && !baseline.contains(orderId)
                        && (restaurantId <= 0 || order.path("restaurantId").asLong(-2) == restaurantId)) {
                    state.setOrder(orderId, text(order, "status", "PENDING"));
                    state.setStatus("RUNNING");
                    state.addEvent("ORDER_SERVICE", "Bắt được đơn thật #" + orderId,
                            "Phát hiện qua GET /api/orders/my-orders", "SUCCESS");
                    return;
                }
            }
            sleepControlled(state, properties.getPollIntervalMillis());
        }
        throw new IllegalStateException("Hết thời gian chờ đơn thật từ Delivery App");
    }

    private void confirmRestaurant(SimulationRunState state, String ownerToken) {
        JsonNode restaurant = state.getRawScenario().path("restaurant");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("restaurantId", restaurant.path("id").asLong());
        request.put("estimatedPrepTime", Math.max(1, restaurant.path("prepTimeMinutes").asInt(10)));
        state.addEvent("GATEWAY", "Nhà hàng xác nhận đơn", "POST /api/restaurants/orders/{orderId}/confirm", "INFO");
        gateway.post("/api/restaurants/orders/" + state.getOrderId() + "/confirm",
                ownerToken, request, state.getCorrelationId());
    }

    private void runDeliveryLoop(SimulationRunState state, String customerToken, String ownerToken) {
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.getRunTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            checkControl(state);
            pollState(state, customerToken);
            String stage = stageFor(state);
            fireTriggers(state, stage, customerToken, ownerToken);
            // A trigger may have changed Order/Delivery asynchronously. Refresh
            // before inspecting offers or moving a shipper so a cancelled run
            // cannot be completed by a stale local snapshot.
            pollState(state, customerToken);

            if (isTerminalOrder(state.getOrderStatus()) || isTerminalDelivery(state.getDeliveryStatus())) {
                return;
            }

            if ("WAIT_SHIPPER_CONFIRM".equals(state.getDeliveryStatus())
                    || "WAIT_SHIPPER_CONFIRM".equals(state.getOrderStatus())
                    || "FINDING_SHIPPER".equals(state.getDeliveryStatus())
                    || "FINDING_SHIPPER".equals(state.getOrderStatus())) {
                inspectOffers(state);
            }

            if ("ASSIGNED".equals(state.getDeliveryStatus()) || "ASSIGNED".equals(state.getOrderStatus())) {
                handleAssigned(state, customerToken, ownerToken);
            } else if ("PICKED_UP".equals(state.getDeliveryStatus())) {
                finishAssignedDelivery(state);
            } else if ("DELIVERING".equals(state.getDeliveryStatus())) {
                completeDelivery(state);
            }

            sleepControlled(state, properties.getPollIntervalMillis());
        }
        throw new IllegalStateException("Scenario vượt quá thời gian chạy tối đa");
    }

    private void inspectOffers(SimulationRunState state) {
        JsonNode configured = state.getRawScenario().path("shippers");
        for (JsonNode shipper : configured) {
            checkControl(state);
            String id = text(shipper, "id", "unknown");
            String token = requiredToken(shipper, "token", "shipper " + id);
            JsonNode response;
            try {
                response = data(gateway.get("/api/deliveries/offers/current", token, state.getCorrelationId()));
            } catch (GatewayClient.GatewayException exception) {
                if (exception.getStatus() == 404 || exception.getStatus() == 204) continue;
                throw exception;
            }
            if (response == null || response.isNull() || !response.isObject()
                    || response.path("orderId").asLong(-1) != state.getOrderId()) {
                state.clearOfferSeen(id);
                continue;
            }

            state.setActiveOfferShipperId(id);
            state.updateShipper(id, "OFFERED", true, null, null);
            long seenAt = state.markOfferSeen(id);
            long reactionDelay = Math.max(0, Math.round(number(shipper, "reactionDelaySeconds", 2)));
            if ((System.currentTimeMillis() - seenAt) < reactionDelay * 1000L) continue;

            String behavior = text(shipper, "behavior", "AUTO_ACCEPT");
            if ("TIMEOUT_IGNORE".equals(behavior)) {
                state.updateCandidate(id, "OFFERED", "Shipper được cấu hình không phản hồi offer");
                continue;
            }

            // DeliveryOfferResponse exposes `expiresAt` (not the internal
            // persistence name `offerExpiresAt`). Include delivery and expiry
            // so a later rematch/generation for the same shipper is a new
            // action rather than being suppressed by the first offer.
            String offerIdentity = response.path("deliveryId").asText("current")
                    + ":" + response.path("expiresAt").asText("current");
            String actionKey = "offer:" + id + ":" + offerIdentity;
            if (!state.markTriggerFired(actionKey)) continue;

            ObjectNode action = objectMapper.createObjectNode();
            action.put("orderId", state.getOrderId());
            action.put("action", "REJECT_AFTER_DELAY".equals(behavior) ? "REJECT" : "ACCEPT");
            action.put("rejectReason", "Scenario Lab configured rejection");
            action.put("estimatedPickupTime", 5);
            action.put("currentLat", number(shipper, "currentLat", number(shipper, "initialLat", 0)));
            action.put("currentLng", number(shipper, "currentLng", number(shipper, "initialLng", 0)));
            gateway.post("/api/deliveries/accept", token, action, state.getCorrelationId());

            if ("REJECT_AFTER_DELAY".equals(behavior)) {
                state.updateShipper(id, "REJECTED", true, null, null);
                state.updateCandidate(id, "REJECTED", "Shipper từ chối offer qua API thật");
                state.addEvent("DELIVERY_SERVICE", "Shipper " + id + " từ chối offer",
                        "POST /api/deliveries/accept action=REJECT", "WARNING");
            } else {
                state.updateShipper(id, "ACCEPTED", true, null, null);
                state.setAssignedShipperId(id);
                state.updateCandidate(id, "SELECTED", null);
                state.addEvent("DELIVERY_SERVICE", "Shipper " + id + " nhận offer",
                        "POST /api/deliveries/accept action=ACCEPT", "SUCCESS");
            }
        }
    }

    private void handleAssigned(SimulationRunState state, String customerToken, String ownerToken) {
        String assignedId = state.getAssignedShipperId();
        if (assignedId == null) return;
        JsonNode shipper = findShipper(state, assignedId);
        if (shipper == null) return;
        String behavior = text(shipper, "behavior", "AUTO_ACCEPT");
        if ("CANCEL_AFTER_ACCEPT".equals(behavior)
                && state.markTriggerFired("cancel-assignment:" + assignedId)) {
            sleepControlled(state, Math.max(0, Math.round(number(shipper, "reactionDelaySeconds", 2))) * 1000);
            String token = requiredToken(shipper, "token", "shipper " + assignedId);
            ObjectNode request = objectMapper.createObjectNode();
            request.put("orderId", state.getOrderId());
            request.put("reason", "Scenario Lab configured cancel-assignment");
            gateway.post("/api/deliveries/cancel-assignment", token, request, state.getCorrelationId());
            state.updateShipper(assignedId, "CANCELLED", true, null, null);
            state.setAssignedShipperId(null);
            state.addEvent("DELIVERY_SERVICE", "Shipper huỷ assignment trước pickup",
                    "POST /api/deliveries/cancel-assignment; runner chờ rematch", "WARNING");
            return;
        }
        finishAssignedDelivery(state);
    }

    private void finishAssignedDelivery(SimulationRunState state) {
        String assignedId = state.getAssignedShipperId();
        JsonNode shipper = findShipper(state, assignedId);
        if (shipper == null || state.getDeliveryId() == null) return;
        String token = requiredToken(shipper, "token", "shipper " + assignedId);
        JsonNode restaurant = state.getRawScenario().path("restaurant");
        moveTo(state, shipper, token, number(restaurant, "lat", 0), number(restaurant, "lng", 0), "di chuyển tới quán");
        transitionDelivery(state, token, "PICKED_UP");
        state.updateShipper(assignedId, "PICKED_UP", true, number(restaurant, "lat", 0), number(restaurant, "lng", 0));
        state.addEvent("TRACKING_WS", "Shipper tới điểm pickup", "Location update thật qua /api/tracking/shipper-locations/update", "INFO");
        finishAssignedDeliveryToCustomer(state, shipper, token);
    }

    private void finishAssignedDeliveryToCustomer(SimulationRunState state, JsonNode shipper, String token) {
        JsonNode customer = state.getRawScenario().path("customer");
        moveTo(state, shipper, token, number(customer, "lat", 0), number(customer, "lng", 0), "di chuyển tới khách");
        transitionDelivery(state, token, "DELIVERING");
        state.updateShipper(state.getAssignedShipperId(), "DELIVERING", true,
                number(customer, "lat", 0), number(customer, "lng", 0));
        transitionDelivery(state, token, "DELIVERED");
        state.updateShipper(state.getAssignedShipperId(), "DELIVERED", true,
                number(customer, "lat", 0), number(customer, "lng", 0));
        state.addEvent("DELIVERY_SERVICE", "Delivery hoàn tất", "Chuỗi status thật: PICKED_UP → DELIVERING → DELIVERED", "SUCCESS");
    }

    private void completeDelivery(SimulationRunState state) {
        JsonNode shipper = findShipper(state, state.getAssignedShipperId());
        if (shipper == null) return;
        String token = requiredToken(shipper, "token", "shipper " + state.getAssignedShipperId());
        transitionDelivery(state, token, "DELIVERED");
    }

    private void transitionDelivery(SimulationRunState state, String token, String nextStatus) {
        if (state.getDeliveryId() == null) return;
        state.addEvent("GATEWAY", "Cập nhật delivery " + nextStatus,
                "PUT /api/deliveries/" + state.getDeliveryId() + "/status?status=" + nextStatus, "INFO");
        gateway.put("/api/deliveries/" + state.getDeliveryId() + "/status?status=" + nextStatus,
                token, null, state.getCorrelationId());
        state.setDeliveryStatus(nextStatus);
        state.setOrderStatus(nextStatus);
    }

    private void moveTo(SimulationRunState state, JsonNode shipper, String token,
                        double targetLat, double targetLng, String label) {
        String id = text(shipper, "id", "unknown");
        double startLat = number(shipper, "currentLat", number(shipper, "initialLat", targetLat));
        double startLng = number(shipper, "currentLng", number(shipper, "initialLng", targetLng));
        for (int index = 1; index <= 3; index++) {
            checkControl(state);
            double ratio = index / 3d;
            double lat = startLat + (targetLat - startLat) * ratio;
            double lng = startLng + (targetLng - startLng) * ratio;
            updateLocation(state, id, token, lat, lng, true);
            sleepControlled(state, 250);
        }
        state.addEvent("TRACKING_WS", "Shipper " + id + " " + label,
                "Location thật đã được gửi qua Tracking REST fallback", "INFO");
    }

    private void updateLocation(SimulationRunState state, String id, String token,
                                double latitude, double longitude, boolean online) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("latitude", latitude);
        request.put("longitude", longitude);
        request.put("isOnline", online);
        gateway.post("/api/tracking/shipper-locations/update", token, request, state.getCorrelationId());
        state.updateShipper(id, online ? "ONLINE" : "OFFLINE", online, latitude, longitude);
        state.addEvent("TRACKING_WS", "Cập nhật vị trí shipper " + id,
                "POST /api/tracking/shipper-locations/update", "INFO");
    }

    private void pollState(SimulationRunState state, String customerToken) {
        if (state.getOrderId() == null) return;
        JsonNode order = data(gateway.get("/api/orders/" + state.getOrderId(), customerToken, state.getCorrelationId()));
        state.setOrderStatus(text(order, "status", state.getOrderStatus()));
        try {
            JsonNode delivery = data(gateway.get("/api/deliveries/order/" + state.getOrderId(), customerToken, state.getCorrelationId()));
            if (delivery != null && delivery.isObject() && !delivery.isNull()) {
                long deliveryId = delivery.path("id").asLong(delivery.path("deliveryId").asLong(-1));
                if (deliveryId > 0) {
                    state.setDelivery(deliveryId, text(delivery, "status", state.getDeliveryStatus()));
                }
                String offered = canonicalShipperId(state, text(delivery, "offeredShipperId", ""));
                String assigned = canonicalShipperId(state, text(delivery, "shipperId", ""));
                if (!offered.isBlank()) state.setActiveOfferShipperId(offered);
                if (!assigned.isBlank() && !"null".equals(assigned)) state.setAssignedShipperId(assigned);
            }
        } catch (GatewayClient.GatewayException exception) {
            if (exception.getStatus() != 404) throw exception;
        }
    }

    private void fireTriggers(SimulationRunState state, String stage,
                              String customerToken, String ownerToken) {
        for (JsonNode trigger : state.getRawScenario().path("triggers")) {
            if (!trigger.path("enabled").asBoolean(false)
                    || !stage.equals(trigger.path("atStage").asText())) {
                continue;
            }
            String type = trigger.path("type").asText();
            String key = "trigger:" + type + ":" + stage;
            if (!state.markTriggerFired(key)) continue;
            long delay = Math.max(0, trigger.path("delaySecondsAfterStage").asLong(0));
            sleepControlled(state, delay * 1000);
            if ("CUSTOMER_CANCEL".equals(type)) {
                ObjectNode request = objectMapper.createObjectNode();
                request.put("reason", "Scenario Lab configured customer cancellation");
                try {
                    gateway.put("/api/orders/" + state.getOrderId() + "/cancel", customerToken,
                            request, state.getCorrelationId());
                    state.addEvent("ORDER_SERVICE", "Khách huỷ đơn tại " + stage,
                            "PUT /api/orders/{id}/cancel; platform tự xử lý compensation", "WARNING");
                } catch (GatewayClient.GatewayException exception) {
                    if (exception.getStatus() == 400 || exception.getStatus() == 409) {
                        state.addEvent("ORDER_SERVICE", "Huỷ đơn bị từ chối đúng policy tại " + stage,
                                "HTTP " + exception.getStatus() + "; đây là expected negative case", "INFO");
                    } else {
                        throw exception;
                    }
                }
            } else if ("RESTAURANT_REJECT".equals(type)) {
                JsonNode restaurant = state.getRawScenario().path("restaurant");
                ObjectNode request = objectMapper.createObjectNode();
                request.put("restaurantId", restaurant.path("id").asLong());
                request.put("reason", "Scenario Lab configured restaurant rejection");
                gateway.post("/api/restaurants/orders/" + state.getOrderId() + "/reject",
                        ownerToken, request, state.getCorrelationId());
                state.addEvent("RESTAURANT_SERVICE", "Nhà hàng từ chối đơn tại " + stage,
                        "POST /api/restaurants/orders/{id}/reject", "WARNING");
            } else if ("SHIPPER_DISCONNECT".equals(type)) {
                JsonNode shipper = state.getRawScenario().path("shippers").path(0);
                if (shipper.isObject()) {
                    String id = text(shipper, "id", "unknown");
                    gateway.post("/api/tracking/shipper-locations/offline",
                            requiredToken(shipper, "token", "shipper " + id), null, state.getCorrelationId());
                    state.updateShipper(id, "OFFLINE", false, null, null);
                    state.addEvent("TRACKING_WS", "Shipper " + id + " mất kết nối",
                            "POST /api/tracking/shipper-locations/offline", "WARNING");
                }
            } else if ("NETWORK_DELAY".equals(type)) {
                state.addEvent("RUNNER", "Inject delay test-only tại " + stage,
                        "Delay được ghi rõ trong timeline; không sửa state backend", "WARNING");
            }
        }
    }

    private void finishAssertions(SimulationRunState state) {
        String terminal = state.getOrderStatus();
        String assigned = state.getAssignedShipperId();
        boolean hasFailure = false;
        boolean hasSkipped = false;
        int assertionIndex = 0;
        for (JsonNode configured : state.getRawScenario().path("assertions")) {
            String id = text(configured, "id", "assertion-" + (++assertionIndex));
            String expectedTerminal = text(configured, "expectedTerminalState", "");
            String expectedShipper = text(configured, "expectedShipperId", "");
            if (configured.has("expectedLedgerCount")) {
                state.assertion(id, "SKIPPED", "Ledger observer chưa được bật trong MVP runner");
                hasSkipped = true;
                continue;
            }
            boolean terminalMatches = expectedTerminal.equals(terminal)
                    // Restaurant rejection is represented canonically as
                    // CANCELLED by OrderStatus.fromExternal; keep the UI's
                    // human-facing REJECTED expectation compatible with that
                    // durable state.
                    || ("REJECTED".equals(expectedTerminal) && "CANCELLED".equals(terminal));
            if (!terminalMatches) {
                state.assertion(id, "FAILED", "Actual terminal=" + terminal + ", expected=" + expectedTerminal);
                hasFailure = true;
            } else if (!expectedShipper.isBlank() && !expectedShipper.equals(assigned)) {
                state.assertion(id, "FAILED", "Actual shipper=" + assigned + ", expected=" + expectedShipper);
                hasFailure = true;
            } else {
                state.assertion(id, "PASSED", "Verified through Gateway state polling: " + terminal);
            }
        }
        if (hasFailure) {
            state.setStatus("FAILED");
        } else if (hasSkipped) {
            state.setStatus("PARTIAL");
            state.addEvent("ASSERTION", "Scenario hoàn tất một phần",
                    "Có assertion cần observer ledger/Kafka chưa được bật", "WARNING");
        } else {
            state.setStatus("PASSED");
        }
    }

    private void validateTarget() {
        try {
            URI target = URI.create(properties.getGatewayBaseUrl());
            String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
            String host = target.getHost() == null ? "" : target.getHost().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || host.isBlank()) {
                throw new IllegalArgumentException("Gateway target phải là URL HTTP(S) có host");
            }
            if (host.contains("prod") || host.contains("production")
                    || host.contains("staging") || host.contains("stage")) {
                throw new IllegalArgumentException("Simulator không được trỏ tới production/staging target");
            }
            if (!properties.isAllowNonLocalTargets()
                    && !properties.getAllowedGatewayHosts().stream()
                    .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
                    .anyMatch(host::equals)) {
                throw new IllegalArgumentException("Simulator chỉ được trỏ tới Gateway host nằm trong allowlist test-only");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("SIMULATOR_GATEWAY_BASE_URL không hợp lệ hoặc không an toàn", exception);
        }
    }

    private List<String> validationErrors(JsonNode scenario) {
        List<String> errors = new ArrayList<>();
        if (scenario == null || !scenario.isObject()) {
            errors.add("scenario phải là JSON object");
            return errors;
        }
        if (!Set.of("HUMAN_ORDER", "SIMULATED_ORDER").contains(text(scenario, "orderMode", ""))) {
            errors.add("orderMode phải là HUMAN_ORDER hoặc SIMULATED_ORDER");
        }
        JsonNode customer = scenario.path("customer");
        JsonNode restaurant = scenario.path("restaurant");
        if (!customer.path("token").isTextual() || customer.path("token").asText().isBlank()) {
            errors.add("customer.token là bắt buộc cho Gateway actor");
        }
        if (restaurant.path("id").asLong(-1) <= 0) errors.add("restaurant.id phải là ID thật trong isolated environment");
        if (restaurant.path("menuItemId").asLong(-1) <= 0) errors.add("restaurant.menuItemId phải là ID thật trong isolated environment");
        if (!coordinateValid(restaurant.path("lat").asDouble(Double.NaN),
                restaurant.path("lng").asDouble(Double.NaN))) {
            errors.add("toạ độ nhà hàng không hợp lệ");
        }
        if (!coordinateValid(customer.path("lat").asDouble(Double.NaN),
                customer.path("lng").asDouble(Double.NaN))) {
            errors.add("toạ độ giao hàng không hợp lệ");
        }
        if (!"COD".equals(text(customer, "paymentMethod", ""))) errors.add("MVP simulator chỉ hỗ trợ COD");
        JsonNode shippers = scenario.path("shippers");
        if (!shippers.isArray() || shippers.isEmpty()) errors.add("Cần ít nhất một shipper");
        if (shippers.isArray() && shippers.size() > properties.getMaxShippers()) {
            errors.add("Số shipper vượt giới hạn " + properties.getMaxShippers());
        }
        Set<String> ids = new HashSet<>();
        if (shippers.isArray()) {
            for (JsonNode shipper : shippers) {
                String id = text(shipper, "id", "");
                if (id.isBlank() || !ids.add(id)) errors.add("shipper id phải có và không trùng: " + id);
                if (!shipper.path("token").isTextual() || shipper.path("token").asText().isBlank()) {
                    errors.add("shipper " + id + ".token là bắt buộc");
                }
                if (!coordinateValid(shipper.path("initialLat").asDouble(Double.NaN),
                        shipper.path("initialLng").asDouble(Double.NaN))) {
                    errors.add("toạ độ ban đầu không hợp lệ cho shipper " + id);
                }
                String behavior = text(shipper, "behavior", "AUTO_ACCEPT");
                if (!Set.of("AUTO_ACCEPT", "REJECT_AFTER_DELAY", "TIMEOUT_IGNORE", "CANCEL_AFTER_ACCEPT")
                        .contains(behavior)) {
                    errors.add("behavior không được hỗ trợ cho shipper " + id + ": " + behavior);
                }
            }
        }
        JsonNode triggers = scenario.path("triggers");
        if (!triggers.isArray()) {
            errors.add("triggers phải là JSON array");
        } else {
            for (JsonNode trigger : triggers) {
                if (!Set.of("CUSTOMER_CANCEL", "RESTAURANT_REJECT", "SHIPPER_DISCONNECT", "NETWORK_DELAY")
                        .contains(text(trigger, "type", ""))) {
                    errors.add("trigger.type không được hỗ trợ: " + text(trigger, "type", ""));
                }
                if (!Set.of("PENDING", "CONFIRMED", "FINDING_SHIPPER", "WAIT_SHIPPER_CONFIRM",
                        "OFFERED", "ASSIGNED", "PICKED_UP", "DELIVERING")
                        .contains(text(trigger, "atStage", ""))) {
                    errors.add("trigger.atStage không được hỗ trợ: " + text(trigger, "atStage", ""));
                }
                if (trigger.path("delaySecondsAfterStage").asLong(0) < 0) {
                    errors.add("trigger.delaySecondsAfterStage không được âm");
                }
            }
        }
        JsonNode assertions = scenario.path("assertions");
        if (!assertions.isArray()) {
            errors.add("assertions phải là JSON array");
        } else {
            for (JsonNode assertion : assertions) {
                if (!Set.of("DELIVERED", "CANCELLED", "SHIPPER_NOT_FOUND", "REJECTED")
                        .contains(text(assertion, "expectedTerminalState", ""))) {
                    errors.add("assertion.expectedTerminalState không được hỗ trợ");
                }
            }
        }
        boolean needsOwner = restaurant.path("autoConfirm").asBoolean(false)
                || containsTrigger(scenario, "RESTAURANT_REJECT");
        if (needsOwner && (!restaurant.path("ownerToken").isTextual()
                || restaurant.path("ownerToken").asText().isBlank())) {
            errors.add("restaurant.ownerToken là bắt buộc khi runner điều khiển nhà hàng");
        }
        return errors;
    }

    private boolean containsTrigger(JsonNode scenario, String type) {
        for (JsonNode trigger : scenario.path("triggers")) {
            if (trigger.path("enabled").asBoolean(false) && type.equals(trigger.path("type").asText())) return true;
        }
        return false;
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Simulator đang bị tắt. Bật SIMULATOR_ENABLED=true trong môi trường test/dev.");
        }
    }

    private SimulationRunState requireRun(String runId) {
        SimulationRunState state = runs.get(runId);
        if (state == null) throw new IllegalArgumentException("Không tìm thấy simulator run: " + runId);
        return state;
    }

    private void checkControl(SimulationRunState state) {
        if (state.isAborted()) throw new RunAbortedException();
        while (state.isPaused()) {
            if (state.isAborted()) throw new RunAbortedException();
            sleep(200);
        }
    }

    private void sleepControlled(SimulationRunState state, long millis) {
        long remaining = Math.max(0, millis);
        while (remaining > 0) {
            checkControl(state);
            long slice = Math.min(remaining, 250);
            sleep(slice);
            remaining -= slice;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunAbortedException();
        }
    }

    private JsonNode findShipper(SimulationRunState state, String id) {
        if (id == null) return null;
        for (JsonNode shipper : state.getRawScenario().path("shippers")) {
            if (id.equals(text(shipper, "id", ""))) return shipper;
        }
        return null;
    }

    private String canonicalShipperId(SimulationRunState state, String rawId) {
        if (rawId == null || rawId.isBlank() || "null".equals(rawId)) return "";
        for (JsonNode shipper : state.getRawScenario().path("shippers")) {
            if (rawId.equals(text(shipper, "id", ""))) return rawId;
            if (shipper.path("userId").asText("").equals(rawId)) {
                return text(shipper, "id", rawId);
            }
        }
        return rawId;
    }

    private String stageFor(SimulationRunState state) {
        if (state.getDeliveryStatus() != null && !"NONE".equals(state.getDeliveryStatus())) {
            return state.getDeliveryStatus();
        }
        return state.getOrderStatus();
    }

    private boolean isTerminalOrder(String status) {
        return "DELIVERED".equals(status) || "CANCELLED".equals(status) || "SHIPPER_NOT_FOUND".equals(status);
    }

    private boolean isTerminalDelivery(String status) {
        return "DELIVERED".equals(status) || "CANCELLED".equals(status) || "SHIPPER_NOT_FOUND".equals(status);
    }

    private JsonNode data(JsonNode response) {
        if (response == null || response.isNull()) return response;
        return response.has("data") ? response.path("data") : response;
    }

    private ArrayNode array(JsonNode node, String field) {
        if (node != null && node.path(field).isArray()) return (ArrayNode) node.path(field);
        return objectMapper.createArrayNode();
    }

    private long positiveId(JsonNode node, String field, String label) {
        long id = node == null ? -1 : node.path(field).asLong(-1);
        if (id <= 0) throw new IllegalStateException("Gateway không trả về " + label + " ID hợp lệ");
        return id;
    }

    private String requiredToken(JsonNode node, String field, String label) {
        String value = text(node, field, "");
        if (value.isBlank()) throw new IllegalArgumentException(label + "." + field + " là bắt buộc");
        return value;
    }

    private String text(JsonNode node, String field, String fallback) {
        return node != null && node.hasNonNull(field) && node.path(field).isValueNode()
                ? node.path(field).asText()
                : fallback;
    }

    private double number(JsonNode node, String field, double fallback) {
        return node != null && node.hasNonNull(field) && node.path(field).isNumber()
                ? node.path(field).asDouble()
                : fallback;
    }

    private boolean coordinateValid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= 8 && lat <= 24 && lng >= 102 && lng <= 110;
    }

    private String safeError(Exception exception) {
        if (exception instanceof GatewayClient.GatewayException gatewayException) {
            return gatewayException.getOperation() + " failed: " + gatewayException.getMessage();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void finishAssertionsForNoOrder(SimulationRunState state) {
        state.setStatus("FAILED");
    }

    private static final class RunAbortedException extends RuntimeException {
    }
}
