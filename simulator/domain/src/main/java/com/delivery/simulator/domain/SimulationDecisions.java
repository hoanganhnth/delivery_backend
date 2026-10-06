package com.delivery.simulator.domain;
import java.util.List;
import java.util.Set;
/** Explicit projection, control and retry decisions, preserving the runner's existing semantics. */
public final class SimulationDecisions {
    private SimulationDecisions() { }
    public static boolean terminalRun(String status) {
        return List.of("PASSED","PARTIAL","FAILED","ABORTED").contains(status);
    }
    public static boolean terminalOrder(String status) {
        return "DELIVERED".equals(status) || "CANCELLED".equals(status) || "SHIPPER_NOT_FOUND".equals(status);
    }
    public static boolean converged(String order, String delivery) {
        if (!terminalOrder(order)) return false;
        if ("DELIVERED".equals(order)) return "DELIVERED".equals(delivery);
        return "NONE".equals(delivery) || "CANCELLED".equals(delivery) || "SHIPPER_NOT_FOUND".equals(delivery);
    }
    public static boolean actorReleaseSafe(Long deliveryId, String status) {
        return deliveryId == null || Set.of("DELIVERED","CANCELLED","SHIPPER_NOT_FOUND","NONE").contains(status);
    }
    public static boolean transientRateLimit(Integer status) { return status != null && status == 429; }
    public static long locationRetryDelayMillis(int attempt) {
        int bounded = Math.max(0,Math.min(8,attempt)); return Math.min(30_000L,1_000L << bounded);
    }
    public static boolean retryLocation(Integer status,int attempt) { return transientRateLimit(status) && attempt < 8; }
    public static boolean coordinateValid(double lat,double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat >= 8 && lat <= 24 && lng >= 102 && lng <= 110;
    }
    public static boolean autoConfirm(String orderStatus, boolean configured, String ownerToken, boolean pendingTriggerFired) {
        return "PENDING".equals(orderStatus) && configured && !ownerToken.isBlank() && !pendingTriggerFired;
    }
    public record ActorAlias(String id,String userId) { }
    public static String canonicalShipper(List<ActorAlias> aliases,String rawId) {
        if (rawId.isBlank() || "null".equals(rawId)) return "";
        for (ActorAlias alias : aliases) {
            if (rawId.equals(alias.id())) return rawId;
            if (alias.userId().equals(rawId)) return alias.id();
        }
        return rawId;
    }
}
