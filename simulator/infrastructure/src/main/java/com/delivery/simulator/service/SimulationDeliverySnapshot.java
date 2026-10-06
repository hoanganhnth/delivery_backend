package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/** Read-only interpretation of the Gateway delivery response and actor aliases. */
record SimulationDeliverySnapshot(long deliveryId, String status, String offeredShipper, String assignedShipper) {
    static Optional<SimulationDeliverySnapshot> parse(JsonNode delivery, JsonNode scenario, String previousStatus) {
        if (delivery == null || !delivery.isObject()) return Optional.empty();
        long id = delivery.path("id").asLong(delivery.path("deliveryId").asLong(-1));
        return Optional.of(new SimulationDeliverySnapshot(id, text(delivery, "status", previousStatus),
                canonicalShipper(scenario, text(delivery, "offeredShipperId", "")),
                canonicalShipper(scenario, text(delivery, "shipperId", ""))));
    }

    private static String canonicalShipper(JsonNode scenario, String rawId) {
        java.util.List<com.delivery.simulator.domain.SimulationDecisions.ActorAlias> aliases = new java.util.ArrayList<>();
        for (JsonNode shipper : scenario.path("shippers")) {
            aliases.add(new com.delivery.simulator.domain.SimulationDecisions.ActorAlias(
                    text(shipper,"id",rawId),shipper.path("userId").asText("")));
        }
        return com.delivery.simulator.domain.SimulationDecisions.canonicalShipper(aliases,rawId);
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) && node.path(field).isTextual() ? node.path(field).asText() : fallback;
    }
}
