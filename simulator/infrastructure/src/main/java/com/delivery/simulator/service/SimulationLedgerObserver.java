package com.delivery.simulator.service;

import com.delivery.identity.contracts.SimulationContext;
import com.delivery.simulator.entity.SimulationLedgerEntry;
import com.delivery.simulator.repository.SimulationLedgerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "simulator", name = "ledger-observer-enabled", havingValue = "true")
public class SimulationLedgerObserver {
    private final ObjectMapper mapper;
    private final SimulationLedgerRepository ledger;
    public SimulationLedgerObserver(ObjectMapper mapper, SimulationLedgerRepository ledger) {
        this.mapper = mapper; this.ledger = ledger;
    }
    @KafkaListener(topics = "${simulator.delivery-completed-topic:delivery.completed}")
    public void observe(String raw) throws Exception {
        JsonNode event = mapper.readTree(raw);
        SimulationContext context = mapper.treeToValue(event.path("simulationContext"), SimulationContext.class);
        context.requireValid();
        if (!context.isSimulation()) return;
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        ledger.findById(eventId).orElseGet(() -> ledger.save(new SimulationLedgerEntry(eventId, context.runId(),
                event.path("orderId").asLong(), event.path("deliveryId").asLong(),
                new BigDecimal(event.path("totalPrice").asText()), Instant.now())));
    }
}
