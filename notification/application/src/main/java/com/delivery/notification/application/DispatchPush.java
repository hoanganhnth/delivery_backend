package com.delivery.notification.application;

import com.delivery.notification.application.api.PushPort;
import com.delivery.notification.domain.PushEligibility;
import java.util.Map;

/** At-least-once token iteration; partial failure intentionally repeats earlier tokens on retry. */
public final class DispatchPush {
    private final PushPort port;
    public DispatchPush(PushPort port) { this.port = port; }

    public void send(Long userId, String title, String body, Map<String, String> data) {
        PushEligibility.validate(userId, title, body);
        if (!port.configured()) return;
        for (Object token : port.tokens(userId)) {
            String value = token.toString();
            if (port.send(value, title, body, data, userId) == PushPort.Outcome.UNREGISTERED) {
                port.removeToken(userId, value);
            }
        }
    }
}
