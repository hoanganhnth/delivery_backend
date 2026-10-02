package com.delivery.user_service.service;

import com.delivery.user.application.api.*;
import com.delivery.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaUserIdentityStatusAdapter implements UserIdentityStatusPort {
    private final UserRepository users;
    @Override @Transactional
    public void mutate(Long principalId, java.util.function.UnaryOperator<UserIdentityProjection> transition) {
        users.findByPrincipalId(principalId).ifPresent(user -> {
            var current = new UserIdentityProjection(user.getIdentityStatus(), user.getIdentityStatusVersion(),
                    user.getIsBlocked(), user.getIsActive(), user.getBlockedAt(), user.getBlockedBy(), user.getBlockReason());
            var next = transition.apply(current);
            if (!next.equals(current)) {
                user.setIdentityStatus(next.status()); user.setIdentityStatusVersion(next.version());
                user.setIsBlocked(next.blocked()); user.setIsActive(next.active());
                user.setBlockedAt(next.blockedAt()); user.setBlockedBy(next.blockedBy()); user.setBlockReason(next.reason());
                users.save(user);
            }
        });
    }
}
