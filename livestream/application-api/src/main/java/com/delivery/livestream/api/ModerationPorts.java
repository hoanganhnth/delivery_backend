package com.delivery.livestream.api;
import java.util.UUID;
public interface ModerationPorts<V> {
    void inspect(UUID room);
    void end(UUID room, Long legacyUser);
    void unpin(UUID room, Long product, Long legacyUser);
    V audit(UUID room, Long principal, String action, String reason, Long product);
}
