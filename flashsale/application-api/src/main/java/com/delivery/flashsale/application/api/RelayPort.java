package com.delivery.flashsale.application.api;

import java.time.LocalDateTime;
import java.util.List;

public interface RelayPort<E> {
    LocalDateTime now();
    List<E> due(LocalDateTime now, int limit);
    void publish(E event) throws Exception;
    void sent(E event, LocalDateTime now);
    int attempts(E event);
    void failed(E event, int attempts, String error);
    void dead(E event, Exception cause);
    void retryAt(E event, LocalDateTime nextAttempt);
}
