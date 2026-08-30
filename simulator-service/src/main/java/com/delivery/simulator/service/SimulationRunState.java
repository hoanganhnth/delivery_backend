package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class SimulationRunState {

    private final ObjectMapper objectMapper;
    private final JsonNode rawScenario;
    // The durable simulation_runs primary key is UUID. Keep the externally
    // returned run id in the same canonical form so persistence and recovery
    // cannot fail after an otherwise valid start request.
    private final String runId = UUID.randomUUID().toString();
    private final String correlationId = "sim-" + UUID.randomUUID();
    private final Instant startedAt = Instant.now();
    private final List<Map<String, Object>> timeline = new ArrayList<>();
    private final List<Map<String, Object>> candidates = new ArrayList<>();
    private final List<Map<String, Object>> algorithmTraces = new ArrayList<>();
    private final List<Map<String, Object>> algorithmComparisons = new ArrayList<>();
    private final List<Map<String, Object>> orders = new ArrayList<>();
    private final Set<Long> observedOrderIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> observedDeliveryIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> deliveryOrderIds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> shippers = new LinkedHashMap<>();
    private final List<Map<String, Object>> assertions = new ArrayList<>();
    private final Set<String> firedTriggers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> offerFirstSeen = new ConcurrentHashMap<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile Consumer<Map<String, Object>> eventObserver;
    private volatile Consumer<Map<String, Object>> assertionObserver;

    private volatile String status = "DRAFT";
    private volatile String orderStatus = "INITIALIZING";
    private volatile String deliveryStatus = "NONE";
    private volatile Long orderId;
    private volatile Long deliveryId;
    private volatile String activeOfferShipperId;
    private volatile String assignedShipperId;
    private volatile String endedAt;
    private volatile boolean paused;
    private volatile boolean aborted;

    SimulationRunState(ObjectMapper objectMapper, JsonNode rawScenario) {
        this.objectMapper = objectMapper;
        this.rawScenario = rawScenario.deepCopy();
        initializeShippers();
        initializeCandidates();
        initializeAssertions();
    }

    String getRunId() {
        return runId;
    }

    String getCorrelationId() {
        return correlationId;
    }

    JsonNode getRawScenario() {
        return rawScenario;
    }

    void setEventObserver(Consumer<Map<String, Object>> eventObserver) {
        this.eventObserver = eventObserver;
    }

    void setAssertionObserver(Consumer<Map<String, Object>> assertionObserver) {
        this.assertionObserver = assertionObserver;
    }

    /**
     * Durable run metadata is observable by operators after a worker restart.
     * It must retain scenario shape for diagnostics without storing credentials
     * used by the active in-memory runner.
     */
    String persistableScenarioJson() {
        return safeScenario().toString();
    }

    synchronized void setStatus(String status) {
        this.status = status;
        if (List.of("PASSED", "PARTIAL", "FAILED", "ABORTED").contains(status)) {
            this.endedAt = Instant.now().toString();
        }
    }

    String getStatus() {
        return status;
    }

    Instant getStartedAt() { return startedAt; }

    boolean isPaused() {
        return paused;
    }

    void pause() {
        paused = true;
        status = "PAUSED";
    }

    void resume() {
        paused = false;
        if (!aborted && !isTerminal()) {
            status = "RUNNING";
        }
    }

    void abort() {
        aborted = true;
        paused = false;
        status = "ABORTED";
        endedAt = Instant.now().toString();
    }

    boolean isAborted() {
        return aborted;
    }

    boolean isTerminal() {
        return List.of("PASSED", "PARTIAL", "FAILED", "ABORTED").contains(status);
    }

    void setOrder(Long orderId, String orderStatus) {
        this.orderId = orderId;
        if (orderId != null && orderId > 0) observedOrderIds.add(orderId);
        if (orderStatus != null) {
            this.orderStatus = orderStatus;
        }
    }

    void setOrderStatus(String orderStatus) {
        if (orderStatus != null && !orderStatus.isBlank()) {
            this.orderStatus = orderStatus;
        }
    }

    void setDelivery(Long deliveryId, String deliveryStatus) {
        this.deliveryId = deliveryId;
        if (deliveryId != null && deliveryId > 0) observedDeliveryIds.add(deliveryId);
        if (deliveryId != null && deliveryId > 0 && orderId != null && orderId > 0) {
            deliveryOrderIds.put(deliveryId, orderId);
        }
        if (deliveryStatus != null) {
            this.deliveryStatus = deliveryStatus;
        }
    }

    void setDeliveryStatus(String deliveryStatus) {
        if (deliveryStatus != null && !deliveryStatus.isBlank()) {
            this.deliveryStatus = deliveryStatus;
        }
    }

    boolean matchesAlgorithmTrace(JsonNode trace) {
        long traceOrderId = trace.path("orderId").asLong(-1);
        long traceDeliveryId = trace.path("deliveryId").asLong(-1);
        if (traceOrderId <= 0 || traceDeliveryId <= 0) {
            return false;
        }
        return observedOrderIds.contains(traceOrderId)
                && observedDeliveryIds.contains(traceDeliveryId)
                && Long.valueOf(traceOrderId).equals(deliveryOrderIds.get(traceDeliveryId));
    }

    synchronized void beginNextOrder(int sequenceNumber) {
        if (orderId != null && (orders.isEmpty()
                || !orderId.equals(orders.get(orders.size() - 1).get("orderId")))) {
            Map<String, Object> previous = new LinkedHashMap<>();
            previous.put("sequence", orders.size() + 1);
            previous.put("orderId", orderId);
            previous.put("deliveryId", deliveryId);
            previous.put("orderStatus", orderStatus);
            previous.put("deliveryStatus", deliveryStatus);
            previous.put("assignedShipperId", assignedShipperId);
            orders.add(previous);
        }
        orderId = null;
        deliveryId = null;
        orderStatus = "INITIALIZING";
        deliveryStatus = "NONE";
        activeOfferShipperId = null;
        assignedShipperId = null;
        addEvent("RUNNER", "Bắt đầu benchmark order #" + sequenceNumber,
                "Cùng actor pool và simulation namespace; counter fairness được giữ lại", "INFO");
    }

    synchronized void finishCurrentOrder() {
        if (orderId == null) return;
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("sequence", orders.size() + 1);
        current.put("orderId", orderId);
        current.put("deliveryId", deliveryId);
        current.put("orderStatus", orderStatus);
        current.put("deliveryStatus", deliveryStatus);
        current.put("assignedShipperId", assignedShipperId);
        orders.add(current);
    }

    synchronized void addAlgorithmTrace(JsonNode trace) {
        Map<String, Object> value = objectMapper.convertValue(trace, LinkedHashMap.class);
        boolean alreadyObserved = algorithmTraces.stream()
                .anyMatch(existing -> String.valueOf(existing.get("eventId"))
                        .equals(String.valueOf(value.get("eventId"))));
        algorithmTraces.removeIf(existing -> String.valueOf(existing.get("eventId"))
                .equals(String.valueOf(value.get("eventId"))));
        algorithmTraces.add(value);
        if (alreadyObserved) {
            return;
        }
        String decision = String.valueOf(value.getOrDefault("decision", "UNKNOWN"));
        String version = String.valueOf(value.getOrDefault("algorithmVersion", "unknown"));
        String title = "Matching trace " + decision + " · " + version;
        String details = "Trace thật từ Match Service; candidate view sau GEO filter";
        addEvent("MATCH_SERVICE", title, details,
                "SHIPPER_SELECTED".equals(decision) ? "SUCCESS" : "WARNING",
                "matching.decision-trace", value);
    }

    synchronized void addAlgorithmComparison(JsonNode comparison) {
        Map<String, Object> value = objectMapper.convertValue(comparison, LinkedHashMap.class);
        String sourceEventId = String.valueOf(value.getOrDefault("sourceEventId", ""));
        String algorithm = String.valueOf(value.getOrDefault("algorithmId", ""));
        String version = String.valueOf(value.getOrDefault("algorithmVersion", ""));
        algorithmComparisons.removeIf(existing -> sourceEventId.equals(String.valueOf(existing.get("sourceEventId")))
                && algorithm.equals(String.valueOf(existing.get("algorithmId")))
                && version.equals(String.valueOf(existing.get("algorithmVersion"))));
        algorithmComparisons.add(value);
        String recommended = String.valueOf(value.getOrDefault("recommendedShipperId", "none"));
        String actual = String.valueOf(value.getOrDefault("actualSelectedShipperId", "none"));
        addEvent("ALGORITHM_SHADOW", "Shadow " + algorithm + " · " + version,
                "Khuyến nghị shipper " + recommended + "; Match thực tế chọn " + actual,
                Boolean.TRUE.equals(value.get("changesSelection")) ? "WARNING" : "INFO",
                "simulation.algorithm-shadow", value);
    }

    Long getOrderId() {
        return orderId;
    }

    Long getDeliveryId() {
        return deliveryId;
    }

    String getOrderStatus() {
        return orderStatus;
    }

    String getDeliveryStatus() {
        return deliveryStatus;
    }

    /** An actor may only re-enter Auth's pool after its last delivery converged. */
    boolean isActorReleaseSafe() {
        return deliveryId == null || Set.of("DELIVERED", "CANCELLED", "SHIPPER_NOT_FOUND", "NONE")
                .contains(deliveryStatus);
    }

    void setActiveOfferShipperId(String shipperId) {
        activeOfferShipperId = shipperId;
    }

    void setAssignedShipperId(String shipperId) {
        assignedShipperId = shipperId;
    }

    String getAssignedShipperId() {
        return assignedShipperId;
    }

    void initializeShippers() {
        JsonNode configured = rawScenario.path("shippers");
        if (!configured.isArray()) {
            return;
        }
        for (JsonNode shipper : configured) {
            String id = text(shipper, "id", UUID.randomUUID().toString());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", id);
            snapshot.put("name", text(shipper, "name", id));
            String locationLabel = text(shipper, "locationLabel", "");
            String address = text(shipper, "address", "");
            if (!locationLabel.isBlank()) snapshot.put("locationLabel", locationLabel);
            if (!address.isBlank()) snapshot.put("address", address);
            snapshot.put("initialLat", number(shipper, "initialLat", 0d));
            snapshot.put("initialLng", number(shipper, "initialLng", 0d));
            snapshot.put("currentLat", number(shipper, "currentLat", number(shipper, "initialLat", 0d)));
            snapshot.put("currentLng", number(shipper, "currentLng", number(shipper, "initialLng", 0d)));
            boolean online = shipper.path("isOnline").asBoolean(false);
            snapshot.put("status", online ? "ONLINE" : "OFFLINE");
            snapshot.put("codBalance", number(shipper, "codBalance", 0d));
            snapshot.put("isOnline", online);
            snapshot.put("behavior", text(shipper, "behavior", "AUTO_ACCEPT"));
            snapshot.put("reactionDelaySeconds", number(shipper, "reactionDelaySeconds", 2d));
            snapshot.put("speedKmH", number(shipper, "speedKmH", 30d));
            if (shipper.has("userId")) {
                snapshot.put("userId", shipper.path("userId").asLong());
            }
            shippers.put(id, snapshot);
        }
    }

    void initializeAssertions() {
        JsonNode configured = rawScenario.path("assertions");
        if (!configured.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode assertion : configured) {
            Map<String, Object> value = objectMapper.convertValue(assertion, LinkedHashMap.class);
            value.putIfAbsent("id", "assertion-" + (++index));
            value.put("status", "PENDING");
            value.remove("actualValue");
            assertions.add(value);
        }
    }

    synchronized void replaceCandidates(List<Map<String, Object>> values) {
        candidates.clear();
        candidates.addAll(values);
    }

    synchronized void updateCandidate(String shipperId, String state, String reason) {
        for (Map<String, Object> candidate : candidates) {
            if (shipperId.equals(String.valueOf(candidate.get("shipperId")))) {
                candidate.put("state", state);
                if (reason != null) {
                    candidate.put("rejectionReason", reason);
                } else {
                    candidate.remove("rejectionReason");
                }
            }
        }
    }

    synchronized void updateShipper(String shipperId, String status, Boolean online,
                                     Double latitude, Double longitude) {
        Map<String, Object> value = shippers.get(shipperId);
        if (value == null) {
            return;
        }
        if (status != null) value.put("status", status);
        if (online != null) {
            value.put("isOnline", online);
            if (!online) value.put("status", "OFFLINE");
        }
        if (latitude != null) value.put("currentLat", latitude);
        if (longitude != null) value.put("currentLng", longitude);
    }

    boolean markTriggerFired(String key) {
        return firedTriggers.add(key);
    }

    boolean isTriggerFiredAtStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return false;
        }
        return firedTriggers.stream().anyMatch(key -> key.endsWith(":" + stage));
    }

    long markOfferSeen(String shipperId) {
        return offerFirstSeen.computeIfAbsent(shipperId, ignored -> System.currentTimeMillis());
    }

    void clearOfferSeen(String shipperId) {
        offerFirstSeen.remove(shipperId);
    }

    synchronized void assertion(String assertionId, String status, String actualValue) {
        for (Map<String, Object> value : assertions) {
            if (assertionId.equals(String.valueOf(value.get("id")))) {
                value.put("status", status);
                value.put("actualValue", actualValue);
                Consumer<Map<String, Object>> observer = assertionObserver;
                if (observer != null) {
                    Map<String, Object> journal = new LinkedHashMap<>();
                    journal.put("source", "ASSERTION");
                    journal.put("title", assertionId);
                    journal.put("assertionId", assertionId);
                    journal.put("status", status);
                    journal.put("actualValue", actualValue);
                    observer.accept(journal);
                }
            }
        }
    }

    void addEvent(String source, String title, String details, String status) {
        addEvent(source, title, details, status, null, null);
    }

    synchronized void addEvent(String source, String title, String details,
                                String status, String topic, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt-" + UUID.randomUUID());
        event.put("timestamp", Instant.now().toString());
        event.put("source", source);
        event.put("title", title);
        if (details != null) event.put("details", details);
        event.put("status", status == null ? "INFO" : status);
        event.put("correlationId", correlationId);
        if (topic != null) event.put("topic", topic);
        if (payload != null) event.put("payload", payload);
        timeline.add(0, event);
        Consumer<Map<String, Object>> observer = eventObserver;
        if (observer != null) {
            observer.accept(new LinkedHashMap<>(event));
        }
        publish();
    }

    void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ignored -> emitters.remove(emitter));
        publishTo(emitter);
    }

    void completeEmitters() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // The browser may have disconnected; completion is best effort.
            }
        }
        emitters.clear();
    }

    synchronized Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("scenario", safeScenario());
        result.put("status", status);
        result.put("orderId", orderId);
        result.put("deliveryId", deliveryId);
        result.put("orderStatus", orderStatus);
        result.put("deliveryStatus", deliveryStatus);
        result.put("activeOfferShipperId", activeOfferShipperId);
        result.put("assignedShipperId", assignedShipperId);
        result.put("startedAt", startedAt.toString());
        result.put("endedAt", endedAt);
        result.put("elapsedSeconds", Duration.between(startedAt, Instant.now()).toSeconds());
        result.put("timeline", new ArrayList<>(timeline));
        result.put("candidates", new ArrayList<>(candidates));
        result.put("algorithmTraces", new ArrayList<>(algorithmTraces));
        result.put("algorithmComparisons", new ArrayList<>(algorithmComparisons));
        List<Map<String, Object>> orderSnapshot = new ArrayList<>(orders);
        if (orderId != null && (orderSnapshot.isEmpty()
                || !orderId.equals(orderSnapshot.get(orderSnapshot.size() - 1).get("orderId")))) {
            Map<String, Object> current = new LinkedHashMap<>();
            current.put("sequence", orderSnapshot.size() + 1);
            current.put("orderId", orderId);
            current.put("deliveryId", deliveryId);
            current.put("orderStatus", orderStatus);
            current.put("deliveryStatus", deliveryStatus);
            current.put("assignedShipperId", assignedShipperId);
            orderSnapshot.add(current);
        }
        result.put("orders", orderSnapshot);
        result.put("liveShippers", new ArrayList<>(shippers.values()));
        result.put("assertions", new ArrayList<>(assertions));
        return result;
    }

    private void publish() {
        for (SseEmitter emitter : emitters) {
            publishTo(emitter);
        }
    }

    private void publishTo(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(sequence.incrementAndGet()))
                    .data(snapshot()));
        } catch (Exception exception) {
            emitters.remove(emitter);
            try {
                emitter.completeWithError(exception);
            } catch (RuntimeException ignored) {
                // Already closed by the client.
            }
        }
    }

    /**
     * Seed the console's candidate table from the scenario before the real
     * Match/Delivery services emit an offer. This is an explicit scenario
     * oracle (configured distance, online flag and COD balance), not a claim
     * that the backend exposed its internal candidate pool. Actual offers and
     * assignment subsequently update these rows.
     */
    private void initializeCandidates() {
        JsonNode configured = rawScenario.path("shippers");
        if (!configured.isArray()) {
            return;
        }
        JsonNode restaurant = rawScenario.path("restaurant");
        JsonNode customer = rawScenario.path("customer");
        double pickupLat = number(restaurant, "lat", 0d);
        double pickupLng = number(restaurant, "lng", 0d);
        double orderAmount = number(restaurant, "menuItemPrice", 0d)
                * Math.max(1d, number(customer, "itemQuantity", 1d));
        List<Map<String, Object>> values = new ArrayList<>();
        for (JsonNode shipper : configured) {
            String id = text(shipper, "id", UUID.randomUUID().toString());
            double latitude = number(shipper, "initialLat", 0d);
            double longitude = number(shipper, "initialLng", 0d);
            double distance = distanceKm(latitude, longitude, pickupLat, pickupLng);
            double codBalance = number(shipper, "codBalance", 0d);
            boolean online = shipper.path("isOnline").asBoolean(false);
            boolean codEligible = codBalance >= orderAmount;
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("shipperId", id);
            candidate.put("shipperName", text(shipper, "name", id));
            candidate.put("distanceKm", round(distance));
            candidate.put("codBalance", codBalance);
            candidate.put("isOnline", online);
            candidate.put("isBusy", false);
            candidate.put("isEligible", online && codEligible);
            candidate.put("selectionScore", round(1d / (1d + distance)));
            candidate.put("generation", 0);
            candidate.put("state", online && codEligible ? "EVALUATED" : "SKIPPED");
            if (!online) {
                candidate.put("rejectionReason", "Scenario config: shipper đang offline");
            } else if (!codEligible) {
                candidate.put("rejectionReason", "Scenario config: ký quỹ COD thấp hơn giá trị đơn");
            }
            values.add(candidate);
        }
        values.sort(Comparator.comparingDouble(value -> ((Number) value.get("distanceKm")).doubleValue()));
        candidates.addAll(values);
    }

    private double distanceKm(double firstLat, double firstLng, double secondLat, double secondLng) {
        if (!Double.isFinite(firstLat) || !Double.isFinite(firstLng)
                || !Double.isFinite(secondLat) || !Double.isFinite(secondLng)) {
            return Double.POSITIVE_INFINITY;
        }
        double lat1 = Math.toRadians(firstLat);
        double lat2 = Math.toRadians(secondLat);
        double deltaLat = Math.toRadians(secondLat - firstLat);
        double deltaLng = Math.toRadians(secondLng - firstLng);
        double haversine = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLng / 2d) * Math.sin(deltaLng / 2d);
        return 6371d * 2d * Math.atan2(Math.sqrt(haversine), Math.sqrt(1d - haversine));
    }

    private double round(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * 1000d) / 1000d;
    }

    private JsonNode safeScenario() {
        JsonNode copy = rawScenario.deepCopy();
        if (!copy.isObject()) {
            return copy;
        }
        if (copy.path("customer").isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("customer")).remove("token");
        }
        if (copy.path("restaurant").isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("restaurant")).remove("ownerToken");
        }
        JsonNode configuredShippers = copy.path("shippers");
        if (configuredShippers.isArray()) {
            configuredShippers.forEach(shipper -> {
                if (shipper.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) shipper).remove("token");
                }
            });
        }
        return copy;
    }

    private String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) && node.path(field).isTextual()
                ? node.path(field).asText()
                : fallback;
    }

    private double number(JsonNode node, String field, double fallback) {
        return node.hasNonNull(field) && node.path(field).isNumber()
                ? node.path(field).asDouble()
                : fallback;
    }
}
