package com.delivery.analytics_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import com.delivery.analytics.domain.SnapshotDecisions;
import com.delivery.analytics.domain.SnapshotDecisions.Item;
import java.util.ArrayList;
import java.util.List;

/** Validates the complete immutable item snapshot before any item projection is written. */
final class AnalyticsItemSnapshotParser {
    private AnalyticsItemSnapshotParser() {}

    static List<Item> parse(JsonNode root) {
        JsonNode items = root.get("items");
        if (items == null || items.isNull()) return List.of();
        if (!items.isArray()) throw new IllegalArgumentException("analytics items must be an array");
        SnapshotDecisions.requireSize(items.size());
        List<Item> result = new ArrayList<>();
        for (JsonNode line : items) {
            long menuItemId = requiredPositiveLong(line, "menuItemId");
            long quantity = requiredPositiveLong(line, "quantity");
            BigDecimal unitPrice = requiredPositiveAmount(line, "unitPrice", "price");
            BigDecimal lineTotal = optionalAmount(line, "lineTotal");
            String name = line.path("menuItemName").asText("UNKNOWN");
            result.add(SnapshotDecisions.item(menuItemId, quantity, unitPrice, lineTotal, name));
        }
        return List.copyOf(result);
    }

    private static long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("analytics item " + field + " must be positive");
        }
        return SnapshotDecisions.positive(value.asLong(), "analytics item " + field + " must be positive");
    }

    private static BigDecimal requiredPositiveAmount(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = optionalAmount(node, field);
            if (value != null) {
                return SnapshotDecisions.price(value);
            }
        }
        throw new IllegalArgumentException("analytics item price is required");
    }

    private static BigDecimal optionalAmount(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("analytics item " + field + " is not numeric", invalid);
        }
    }
}
