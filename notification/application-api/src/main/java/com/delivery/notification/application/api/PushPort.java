package com.delivery.notification.application.api;

import java.util.Map;
import java.util.Set;

/** Provider exceptions/classification and message construction stay in the adapter. */
public interface PushPort {
    enum Outcome { SENT, UNREGISTERED }
    boolean configured();
    Set<Object> tokens(Long userId);
    Outcome send(String token, String title, String body, Map<String, String> data, Long userId);
    void removeToken(Long userId, String token);
}
