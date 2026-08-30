package com.delivery.simulator.service;

import com.delivery.simulator.entity.SimulationRunJournalEntry;
import com.delivery.simulator.repository.SimulationRunJournalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Append-only, credential-redacted timeline journal for post-restart audit. */
@Service
public class SimulationRunJournalService {
    private final SimulationRunJournalRepository entries;
    private final ObjectMapper mapper;
    public SimulationRunJournalService(SimulationRunJournalRepository entries, ObjectMapper mapper) {
        this.entries = entries; this.mapper = mapper;
    }
    public void record(UUID runId, Map<String, Object> event) {
        if (runId == null || event == null) return;
        JsonNode payload = mapper.valueToTree(event);
        redact(payload);
        entries.save(new SimulationRunJournalEntry(runId, Instant.now(),
                text(event, "source", "RUNNER"), text(event, "title", "event"), payload.toString()));
    }
    public List<Map<String, Object>> entries(UUID runId) {
        if (runId == null) return List.of();
        return entries.findByRunIdOrderByIdAsc(runId).stream().map(entry -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("recordedAt", entry.getRecordedAt());
            value.put("source", entry.getSource());
            value.put("title", entry.getTitle());
            try { value.put("payload", mapper.readTree(entry.getPayloadJson())); }
            catch (Exception invalid) { value.put("payload", Map.of("journalError", "invalid payload")); }
            return value;
        }).toList();
    }
    private String text(Map<String, Object> value, String key, String fallback) {
        Object raw = value.get(key); return raw == null ? fallback : String.valueOf(raw);
    }
    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("token"); object.remove("accessToken"); object.remove("ownerToken");
            object.fields().forEachRemaining(field -> redact(field.getValue()));
        } else if (node.isArray()) node.forEach(this::redact);
    }
}
