package com.delivery.user.application.api;

import java.util.List;

/** Application boundary for delivery-address operations. */
public interface UserAddressUseCase {

    List<UserAddressResult> byUserId(Long userId);

    UserAddressResult byId(Long id);

    UserAddressResult create(CreateUserAddressCommand command);

    UserAddressResult update(UpdateUserAddressCommand command);

    void delete(Long id);

    UserAddressResult setDefault(Long id);
}
