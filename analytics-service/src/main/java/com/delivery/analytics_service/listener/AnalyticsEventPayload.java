package com.delivery.analytics_service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Validates event identities before any receipt/projection or acknowledgment. */
final class AnalyticsEventPayload {
    private AnalyticsEventPayload() { }

    static JsonNode read(ObjectMapper mapper, String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("analytics payload must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("analytics payload is not valid JSON", invalid);
        }
    }

    static long requiredId(JsonNode root, String name) {
        JsonNode value = root.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("analytics " + name + " must be a positive integral ID");
        }
        return value.longValue();
    }

    static Long optionalId(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value == null || value.isNull() ? null : requiredId(root, name);
    }

    static double amountOrZero(JsonNode root) {
        JsonNode value = root.get("amount");
        if (value == null || value.isNull()) return 0.0;
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException("analytics amount must be a finite number");
        }
        return value.doubleValue();
    }
}
