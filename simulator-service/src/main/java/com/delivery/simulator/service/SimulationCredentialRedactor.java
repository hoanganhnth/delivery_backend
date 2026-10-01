package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Removes the existing runtime credential keys without mutating source data. */
final class SimulationCredentialRedactor {
    private SimulationCredentialRedactor() { }

    static JsonNode redactedCopy(JsonNode source) {
        JsonNode copy = source.deepCopy();
        redact(copy);
        return copy;
    }

    private static void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("token");
            object.remove("accessToken");
            object.remove("ownerToken");
            object.fields().forEachRemaining(field -> redact(field.getValue()));
        } else if (node.isArray()) {
            node.forEach(SimulationCredentialRedactor::redact);
        }
    }
}
