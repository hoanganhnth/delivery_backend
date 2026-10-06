package com.delivery.promotion.application.api;

import java.time.LocalDateTime;
import java.util.List;

/** lock includes the original identity/principal fences and, for bulk, ordered line locks. */
public interface ReservationTransitionPort<S, R> {
    S lock();
    PromotionCommands.ReservationState state(S reservation);
    LocalDateTime now();
    void transition(S reservation, String target);
    R result(S reservation);
    RuntimeException conflict(String message);
    List<S> expiryCandidates();
    S lockCandidate(S candidate);
}
