package com.delivery.user.application;

import com.delivery.user.application.api.CreateUserAddressCommand;
import com.delivery.user.application.api.UpdateUserAddressCommand;
import com.delivery.user.application.api.UserAddressPort;
import com.delivery.user.application.api.UserAddressResult;
import com.delivery.user.application.api.UserAddressUseCase;
import java.util.List;
import java.util.Objects;

/** Coordinates delivery-address operations through the persistence boundary. */
public final class DefaultUserAddressUseCase implements UserAddressUseCase {

    private final UserAddressPort addressPort;

    public DefaultUserAddressUseCase(UserAddressPort addressPort) {
        this.addressPort = Objects.requireNonNull(addressPort, "addressPort");
    }

    @Override
    public List<UserAddressResult> byUserId(Long userId) {
        return addressPort.byUserId(Objects.requireNonNull(userId, "userId"));
    }

    @Override
    public UserAddressResult byId(Long id) {
        return addressPort.byId(Objects.requireNonNull(id, "id"));
    }

    @Override
    public UserAddressResult create(CreateUserAddressCommand command) {
        Objects.requireNonNull(command, "command");
        return addressPort.withOwnerLock(command.userId(), () -> {
            boolean isDefault = Boolean.TRUE.equals(command.isDefault());
            if (isDefault) {
                addressPort.clearDefaults(command.userId(), null);
            }
            return addressPort.create(command, isDefault);
        });
    }

    @Override
    public UserAddressResult update(UpdateUserAddressCommand command) {
        Objects.requireNonNull(command, "command");
        UserAddressResult before = byId(command.id());
        return addressPort.withOwnerLock(before.userId(), () -> {
            UserAddressResult current = byId(command.id());
            Boolean isDefault = command.isDefault() == null
                    ? current.isDefault() : command.isDefault();
            if (Boolean.TRUE.equals(command.isDefault())) {
                addressPort.clearDefaults(current.userId(), current.id());
            }
            return addressPort.update(command, isDefault);
        });
    }

    @Override
    public void delete(Long id) {
        UserAddressResult before = byId(id);
        addressPort.withOwnerLock(before.userId(), () -> {
            UserAddressResult current = byId(id);
            addressPort.delete(id);
            if (Boolean.TRUE.equals(current.isDefault())) {
                addressPort.latestByUserId(current.userId())
                        .ifPresent(next -> addressPort.setDefault(next.id()));
            }
            return null;
        });
    }

    @Override
    public UserAddressResult setDefault(Long id) {
        UserAddressResult before = byId(id);
        return addressPort.withOwnerLock(before.userId(), () -> {
            UserAddressResult current = byId(id);
            addressPort.clearDefaults(current.userId(), id);
            return addressPort.setDefault(id);
        });
    }
}
