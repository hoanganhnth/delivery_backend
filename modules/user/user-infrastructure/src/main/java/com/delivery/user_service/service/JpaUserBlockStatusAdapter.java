package com.delivery.user_service.service;

import com.delivery.user.application.api.UpdateUserBlockStatusCommand;
import com.delivery.user.application.api.UserBlockStatusPort;
import com.delivery.user.application.api.UserBlockStatusResult;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Objects;
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
    public UserBlockStatusResult update(UpdateUserBlockStatusCommand command) {
        Objects.requireNonNull(command, "command");
        User user = users.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean blocked = Boolean.TRUE.equals(command.blocked());
        if (!Objects.equals(user.getIsBlocked(), blocked)) {
            user.setIsBlocked(blocked);
            user.setIsActive(!blocked);
            if (blocked) {
                user.setBlockedAt(LocalDateTime.now());
                user.setBlockedBy(command.adminId());
                user.setBlockReason(command.reason() == null ? "No reason provided" : command.reason());
            } else {
                user.setBlockedAt(null);
                user.setBlockedBy(null);
                user.setBlockReason(null);
            }
            users.save(user);
        }
        return new UserBlockStatusResult(user.getId(), user.getIsBlocked(), user.getIsActive(),
                user.getBlockedAt(), user.getBlockedBy(), user.getBlockReason());
    }
}
