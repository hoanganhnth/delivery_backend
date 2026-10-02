package com.delivery.user.application;

import com.delivery.user.application.api.UpdateUserBlockStatusCommand;
import com.delivery.user.application.api.UserBlockStatusPort;
import com.delivery.user.application.api.UserBlockStatusResult;
import com.delivery.user.application.api.UserBlockStatusUseCase;
import java.util.Objects;

/** Coordinates Auth-owned block-status projection through its persistence port. */
public final class DefaultUserBlockStatusUseCase implements UserBlockStatusUseCase {

    private final UserBlockStatusPort blockStatusPort;

    public DefaultUserBlockStatusUseCase(UserBlockStatusPort blockStatusPort) {
        this.blockStatusPort = Objects.requireNonNull(blockStatusPort, "blockStatusPort");
    }

    @Override
    public UserBlockStatusResult update(UpdateUserBlockStatusCommand command) {
        Objects.requireNonNull(command, "command");
        return blockStatusPort.mutate(command.userId(), current -> {
            boolean blocked = Boolean.TRUE.equals(command.blocked());
            if (Objects.equals(current.blocked(), blocked)) {
                return current;
            }
            return new UserBlockStatusResult(current.userId(), blocked, !blocked,
                    blocked ? java.time.LocalDateTime.now() : null,
                    blocked ? command.adminId() : null,
                    blocked ? (command.reason() == null ? "No reason provided" : command.reason()) : null);
        });
    }
}
