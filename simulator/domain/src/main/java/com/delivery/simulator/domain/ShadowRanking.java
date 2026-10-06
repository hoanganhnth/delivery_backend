package com.delivery.simulator.domain;
import java.util.*;
/** Read-only shadow recommendations; never an input to Match. Ties preserve input order. */
public final class ShadowRanking {
    public static final double DEFAULT_SPEED = 30d;
    public static final double FAIRNESS_PENALTY = 0.03d;
    private ShadowRanking() { }
    public record Actor(long id,double speed,long completed) { }
    public record Candidate(long id,double distance,boolean offline,boolean codIneligible,
                            boolean hasReasons,String firstReason,String state,String rejectionState) { }
    public record Score(long id,double distance,double speed,boolean eligible,double eta,
                        long completed,double fairness,double combined,String exclusionReason) { }
    public record Ranking(long recommended,List<Score> scores) {
        public Ranking { scores = List.copyOf(scores); }
    }
    public static Ranking rank(List<Candidate> candidates,List<Actor> actors,boolean balanced) {
        Map<Long,Double> speeds = new HashMap<>(); Map<Long,Long> completed = new HashMap<>();
        for (Actor actor : actors) {
            if (actor.id() > 0 && Double.isFinite(actor.speed()) && actor.speed() > 0d) speeds.put(actor.id(),actor.speed());
            if (actor.id() > 0) completed.put(actor.id(),Math.max(0L,actor.completed()));
        }
        List<Score> scores = new ArrayList<>(); long recommended = -1L; double best = Double.MAX_VALUE;
        for (Candidate candidate : candidates) {
            double speed = speeds.getOrDefault(candidate.id(),DEFAULT_SPEED);
            boolean eligible = candidate.id() > 0 && Double.isFinite(candidate.distance()) && candidate.distance() >= 0d
                    && speed > 0d && !candidate.offline() && !candidate.codIneligible() && !candidate.hasReasons()
                    && !"REJECTED".equalsIgnoreCase(candidate.state());
            double eta = eligible ? candidate.distance() / speed * 60d : Double.NaN;
            long count = completed.getOrDefault(candidate.id(),0L);
            double fairness = eligible ? count * FAIRNESS_PENALTY : Double.NaN;
            double combined = eligible ? eta + fairness : Double.NaN;
            scores.add(new Score(candidate.id(),candidate.distance(),speed,eligible,eta,count,fairness,combined,
                    eligible ? null : exclusionReason(candidate,speed)));
            double value = balanced ? combined : eta;
            if (eligible && value < best) { best = value; recommended = candidate.id(); }
        }
        return new Ranking(recommended,scores);
    }
    private static String exclusionReason(Candidate candidate,double speed) {
        if (candidate.id() <= 0) return "MISSING_SHIPPER_ID";
        if (!Double.isFinite(candidate.distance()) || candidate.distance() < 0d) return "MISSING_DISTANCE";
        // Speed map admits finite positive values only; preserve the unreachable legacy branch.
        if (!Double.isFinite(speed) || speed <= 0d) return "INVALID_SPEED";
        if (candidate.hasReasons()) return candidate.firstReason();
        if (candidate.offline()) return "OFFLINE";
        if (candidate.codIneligible()) return "COD_INELIGIBLE";
        return candidate.rejectionState();
    }
}
