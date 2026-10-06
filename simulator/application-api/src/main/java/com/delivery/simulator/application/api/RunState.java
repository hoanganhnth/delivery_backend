package com.delivery.simulator.application.api;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
/** In-memory observation/control boundary; credentials are never persisted by application code. */
public interface RunState {
    String getRunId();
    String getCorrelationId();
    JsonNode getRawScenario();
    void setEventObserver(Consumer<Map<String, Object>> eventObserver);
    void setAssertionObserver(Consumer<Map<String, Object>> assertionObserver);
    String persistableScenarioJson();
    void setStatus(String status);
    String getStatus();
    Instant getStartedAt();
    boolean isPaused();
    void pause();
    void resume();
    void abort();
    boolean isAborted();
    boolean isTerminal();
    void setOrder(Long orderId, String orderStatus);
    void setOrderStatus(String orderStatus);
    void setDelivery(Long deliveryId, String deliveryStatus);
    void setDeliveryStatus(String deliveryStatus);
    boolean matchesAlgorithmTrace(JsonNode trace);
    void beginNextOrder(int sequenceNumber);
    void finishCurrentOrder();
    void addAlgorithmTrace(JsonNode trace);
    void addAlgorithmComparison(JsonNode comparison);
    Long getOrderId();
    Long getDeliveryId();
    String getOrderStatus();
    String getDeliveryStatus();
    boolean isActorReleaseSafe();
    void setActiveOfferShipperId(String shipperId);
    void setAssignedShipperId(String shipperId);
    String getAssignedShipperId();
    void updateCandidate(String shipperId, String state, String reason);
    void updateShipper(String shipperId, String status, Boolean online,
                                     Double latitude, Double longitude);
    boolean markTriggerFired(String key);
    boolean isTriggerFiredAtStage(String stage);
    long markOfferSeen(String shipperId);
    void clearOfferSeen(String shipperId);
    void assertion(String assertionId, String status, String actualValue);
    void addEvent(String source, String title, String details, String status);
    void addEvent(String source, String title, String details,
                                String status, String topic, Object payload);
    void completeEmitters();
    Map<String, Object> snapshot();
}
