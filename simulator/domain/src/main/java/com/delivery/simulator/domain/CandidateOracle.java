package com.delivery.simulator.domain;
import java.util.*;
/** Scenario-only oracle. This does not represent Match's actual candidate stream. */
public final class CandidateOracle {
    private CandidateOracle() { }
    public record Input(String id,String name,double lat,double lng,double codBalance,boolean online) { }
    public record Candidate(String id,String name,double distance,double codBalance,boolean online,
                            boolean eligible,double score,String state,String reason) { }
    public static List<Candidate> evaluate(List<Input> inputs,double pickupLat,double pickupLng,double price,double quantity) {
        double amount = price * Math.max(1d,quantity);
        List<Candidate> candidates = new ArrayList<>();
        for (Input input : inputs) {
            double distance = distance(input.lat(),input.lng(),pickupLat,pickupLng);
            boolean cod = input.codBalance() >= amount;
            boolean eligible = input.online() && cod;
            candidates.add(new Candidate(input.id(),input.name(),round(distance),input.codBalance(),input.online(),eligible,
                    round(1d / (1d + distance)),eligible ? "EVALUATED" : "SKIPPED",
                    !input.online() ? "Scenario config: shipper đang offline"
                            : !cod ? "Scenario config: ký quỹ COD thấp hơn giá trị đơn" : null));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        return List.copyOf(candidates);
    }
    private static double distance(double a,double b,double c,double d) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || !Double.isFinite(d)) return Double.POSITIVE_INFINITY;
        double lat1 = Math.toRadians(a),lat2 = Math.toRadians(c);
        double deltaLat = Math.toRadians(c-a),deltaLng = Math.toRadians(d-b);
        double haversine = Math.sin(deltaLat/2d)*Math.sin(deltaLat/2d)
                + Math.cos(lat1)*Math.cos(lat2)*Math.sin(deltaLng/2d)*Math.sin(deltaLng/2d);
        return 6371d*2d*Math.atan2(Math.sqrt(haversine),Math.sqrt(1d-haversine));
    }
    private static double round(double value) { return Double.isFinite(value) ? Math.round(value*1000d)/1000d : value; }
}
