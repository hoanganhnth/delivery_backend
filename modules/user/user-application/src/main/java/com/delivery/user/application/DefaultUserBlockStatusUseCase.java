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
        return blockStatusPort.update(Objects.requireNonNull(command, "command"));
    }
}
