package com.delivery.user.application.api;

public interface UserIdentityStatusPort {
    void mutate(Long principalId, java.util.function.UnaryOperator<UserIdentityProjection> transition);
}
