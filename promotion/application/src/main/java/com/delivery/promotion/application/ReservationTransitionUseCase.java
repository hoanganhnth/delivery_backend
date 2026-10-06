package com.delivery.promotion.application;

import com.delivery.promotion.application.api.PromotionCommands;
import com.delivery.promotion.application.api.ReservationTransitionPort;
import com.delivery.promotion.domain.ReservationPolicy;

public final class ReservationTransitionUseCase {
    public <S, R> R transition(PromotionCommands.Transition command, ReservationTransitionPort<S, R> port) {
        S reservation = port.lock();
        var state = port.state(reservation);
        if (command.commit()) {
            var decision = ReservationPolicy.commit(state.state(), state.expiresAt(), port.now(), command.bulk());
            if (decision.failure() != null) throw port.conflict(decision.failure());
            if (decision.apply()) port.transition(reservation, "COMMITTED");
        } else if (ReservationPolicy.release(state.state())) {
            port.transition(reservation, "RELEASED");
        }
        return port.result(reservation);
    }

    public <S, R> int expire(ReservationTransitionPort<S, R> port) {
        int count = 0;
        for (S candidate : port.expiryCandidates()) {
            S reservation = port.lockCandidate(candidate);
            if (reservation != null) {
                var state = port.state(reservation);
                if (ReservationPolicy.expire(state.state(), state.expiresAt(), port.now())) {
                    port.transition(reservation, "EXPIRED");
                    count++;
                }
            }
        }
        return count;
    }
}
