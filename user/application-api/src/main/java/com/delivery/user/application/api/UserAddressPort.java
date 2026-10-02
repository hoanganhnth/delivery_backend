package com.delivery.user.application.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Persistence boundary for delivery-address operations. */
public interface UserAddressPort {

    List<UserAddressResult> byUserId(Long userId);

    UserAddressResult byId(Long id);

    <T> T withOwnerLock(Long userId, Supplier<T> operation);

    void clearDefaults(Long userId, Long exceptId);

    Optional<UserAddressResult> latestByUserId(Long userId);

    UserAddressResult create(CreateUserAddressCommand command, boolean isDefault);

    UserAddressResult update(UpdateUserAddressCommand command, Boolean isDefault);

    void delete(Long id);

    UserAddressResult setDefault(Long id);
}
