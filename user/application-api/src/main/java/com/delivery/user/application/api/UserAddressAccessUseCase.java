package com.delivery.user.application.api;

public interface UserAddressAccessUseCase {
    boolean canAccess(Long ownerId, UserActor actor);
}
