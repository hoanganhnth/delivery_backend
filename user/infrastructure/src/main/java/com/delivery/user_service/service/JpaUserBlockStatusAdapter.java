package com.delivery.user_service.service;

import com.delivery.user.application.api.UserBlockStatusPort;
import com.delivery.user.application.api.UserBlockStatusResult;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** JPA adapter for the Auth-to-User block-status projection. */
@Component
@RequiredArgsConstructor
public class JpaUserBlockStatusAdapter implements UserBlockStatusPort {

    private final UserRepository users;

    @Override
    @Transactional
    public UserBlockStatusResult mutate(Long userId,
            java.util.function.UnaryOperator<UserBlockStatusResult> transition) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        UserBlockStatusResult current = new UserBlockStatusResult(user.getId(), user.getIsBlocked(), user.getIsActive(),
                user.getBlockedAt(), user.getBlockedBy(), user.getBlockReason());
        UserBlockStatusResult next = transition.apply(current);
        if (!next.equals(current)) {
            user.setIsBlocked(next.blocked());
            user.setIsActive(next.active());
            user.setBlockedAt(next.blockedAt());
            user.setBlockedBy(next.blockedBy());
            user.setBlockReason(next.reason());
            users.save(user);
        }
        return next;
    }
}
