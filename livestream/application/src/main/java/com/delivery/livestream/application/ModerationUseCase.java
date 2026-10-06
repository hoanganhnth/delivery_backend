package com.delivery.livestream.application;
import com.delivery.livestream.api.ModerationPorts;
import com.delivery.livestream.domain.LivestreamPolicy;
import java.util.UUID;
public final class ModerationUseCase<V> {
    private final ModerationPorts<V> ports;
    public ModerationUseCase(ModerationPorts<V> ports) { this.ports = ports; }
    public V moderate(UUID room, Long principal, Long legacyUser, boolean admin, String action, String reason, Long product) {
        LivestreamPolicy.moderator(principal, admin);
        switch (action) {
            case "WARN" -> ports.inspect(room);
            case "FORCE_END" -> ports.end(room, legacyUser);
            case "UNPIN" -> ports.unpin(room, product, legacyUser);
            default -> throw new IllegalArgumentException(action);
        }
        return ports.audit(room, principal, action, reason.trim(), product);
    }
}
