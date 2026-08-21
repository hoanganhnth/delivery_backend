package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Read-only observer for Match decision traces. It uses its own consumer group
 * and never acknowledges or mutates a business service aggregate.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "simulator", name = "kafka-observer-enabled", havingValue = "true")
public class AlgorithmDecisionTraceObserver {

    private final ObjectMapper objectMapper;
    private final SimulationService simulationService;

    public AlgorithmDecisionTraceObserver(ObjectMapper objectMapper, SimulationService simulationService) {
        this.objectMapper = objectMapper;
        this.simulationService = simulationService;
    }

    @KafkaListener(
            topics = "${simulator.kafka-decision-trace-topic:matching.decision-trace}",
            groupId = "${simulator.kafka-observer-group-id:simulator-algorithm-observer}")
    public void observe(String rawPayload) {
        try {
            JsonNode trace = objectMapper.readTree(rawPayload);
            if (!trace.isObject()
                    || trace.path("orderId").asLong(-1) <= 0
                    || trace.path("deliveryId").asLong(-1) <= 0
                    || !trace.path("eventId").isTextual()
                    || !trace.path("commandEventId").isTextual()
                    || trace.path("eventVersion").asInt(0) <= 0
                    || !trace.path("algorithmId").isTextual()
                    || !trace.path("algorithmVersion").isTextual()
                    || !trace.path("decision").isTextual()
                    || !trace.path("stages").isArray()
                    || !trace.path("candidates").isArray()) {
                throw new IllegalArgumentException(
                        "Decision trace requires versioned eventId/command/order/delivery/algorithm/stages/candidates");
            }
            simulationService.recordAlgorithmTrace(trace);
        } catch (Exception exception) {
            // A malformed observer record must be visible in logs and retried
            // by Kafka; it must never be translated into a business action.
            log.warn("Cannot consume matching decision trace: {}", exception.getMessage());
            throw new IllegalStateException("Invalid matching decision trace", exception);
        }
    }
}
