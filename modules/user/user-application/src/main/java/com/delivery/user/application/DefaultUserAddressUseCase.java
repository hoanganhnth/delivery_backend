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
        return addressPort.create(Objects.requireNonNull(command, "command"));
    }

    @Override
    public UserAddressResult update(UpdateUserAddressCommand command) {
        return addressPort.update(Objects.requireNonNull(command, "command"));
    }

    @Override
    public void delete(Long id) {
        addressPort.delete(Objects.requireNonNull(id, "id"));
    }

    @Override
    public UserAddressResult setDefault(Long id) {
        return addressPort.setDefault(Objects.requireNonNull(id, "id"));
    }
}
