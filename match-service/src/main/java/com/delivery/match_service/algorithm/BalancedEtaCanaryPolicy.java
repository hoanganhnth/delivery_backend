package com.delivery.match_service.algorithm;

import com.delivery.match_service.config.MatchingAlgorithmProperties;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Stateless, deterministic active-profile selection and candidate ranking. */
@Component
public class BalancedEtaCanaryPolicy {
    public static final Profile NEAREST_COD = new Profile("nearest-cod", "v1");
    public static final Profile BALANCED_ETA = new Profile("balanced-eta", "v1");

    private final MatchingAlgorithmProperties properties;

    public BalancedEtaCanaryPolicy(MatchingAlgorithmProperties properties) {
        this.properties = properties;
    }

    public Profile select(UUID eventId) {
        int percent = Math.max(0, Math.min(100, properties.getCanaryPercent()));
        if (!properties.isEnabled() || percent == 0 || eventId == null) return NEAREST_COD;
        if (percent == 100 || Math.floorMod(eventId.hashCode(), 100) < percent) return BALANCED_ETA;
        return NEAREST_COD;
    }

    public List<NearbyShipperResponse> rank(Profile profile, List<NearbyShipperResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) return candidates == null ? List.of() : candidates;
        List<NearbyShipperResponse> ranked = new ArrayList<>(candidates);
        if (!BALANCED_ETA.equals(profile)) return ranked;
        double speed = properties.getEtaSpeedKmPerMinute();
        if (!Double.isFinite(speed) || speed <= 0) {
            throw new IllegalStateException("balanced-eta requires a positive ETA speed");
        }
        double penalty = properties.getFairnessPenaltyMinutesPerCompletedDelivery();
        if (!Double.isFinite(penalty) || penalty < 0) {
            throw new IllegalStateException("balanced-eta fairness penalty must be non-negative");
        }
        ranked.forEach(candidate -> candidate.setCombinedScoreMinutes(score(candidate, speed, penalty)));
        ranked.sort(Comparator.comparing(NearbyShipperResponse::getCombinedScoreMinutes)
                .thenComparing(NearbyShipperResponse::getDistanceKm)
                .thenComparing(NearbyShipperResponse::getShipperId));
        return ranked;
    }

    private double score(NearbyShipperResponse candidate, double speed, double penalty) {
        double distance = candidate.getDistanceKm();
        long completed = Math.max(0L, candidate.getCompletedDeliveries());
        return BigDecimal.valueOf(distance / speed + completed * penalty)
                .setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    public record Profile(String id, String version) { }
}
