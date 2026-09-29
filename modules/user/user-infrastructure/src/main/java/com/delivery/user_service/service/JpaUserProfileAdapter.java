package com.delivery.user_service.service;

import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UpdateProfileCommand;
import com.delivery.user.application.api.UserProfilePort;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** JPA adapter for profile commands; the host keeps its legacy DTO facade. */
@Component
@RequiredArgsConstructor
public class JpaUserProfileAdapter implements UserProfilePort {

    private final UserRepository users;
    private final IdentityOutboxService identityOutbox;

    @Override
    @Transactional
    public UserProfileResult create(CreateUserCommand command) {
        validateProvisioningCommand(command);
        User current = users.findByPrincipalId(command.principalId())
                .map(existing -> requireSameProvisioningIdentity(existing, command))
                .orElseGet(() -> provision(command));
        identityOutbox.profileCreated(current.getPrincipalId(), current.getId());
        return toResult(current);
    }

    @Override
    @Transactional
    public UserProfileResult update(UpdateProfileCommand command) {
        Objects.requireNonNull(command, "command");
        User user = users.findById(command.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setFullName(command.fullName());
        user.setPhone(command.phone());
        user.setDob(command.dob());
        user.setAvatarUrl(command.avatarUrl());
        user.setAddress(command.address());
        return toResult(users.save(user));
    }

    private User provision(CreateUserCommand command) {
        users.findByEmailIgnoreCase(command.email()).ifPresent(existing -> {
            if (!command.principalId().equals(existing.getPrincipalId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "email is already linked to a different auth identity");
            }
            requireSameProvisioningIdentity(existing, command);
        });

        users.insertProvisionedUserIfAbsent(
                command.authId(), command.principalId(), command.email(), command.role(),
                command.fullName(), command.phone(), command.dob(), command.avatarUrl(), command.address());
        return users.findByPrincipalId(command.principalId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "User provisioning could not be completed"));
    }

    private void validateProvisioningCommand(CreateUserCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.authId() == null || command.principalId() == null
                || command.email() == null || command.email().isBlank()
                || command.role() == null || command.role().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authId, principalId, email and role are required for user provisioning");
        }
        if (!command.authId().equals(command.principalId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authId and principalId must identify the same Auth account");
        }
    }

    private User requireSameProvisioningIdentity(User existing, CreateUserCommand command) {
        if (!Objects.equals(existing.getAuthId(), command.authId())
                || !Objects.equals(existing.getPrincipalId(), command.principalId())
                || !existing.getEmail().equalsIgnoreCase(command.email())
                || !existing.getRole().equals(command.role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "principalId is already linked to a different user identity");
        }
        return existing;
    }

    static UserProfileResult toResult(User user) {
        return new UserProfileResult(
                user.getId(), user.getAuthId(), user.getPrincipalId(), user.getIdentityStatus(),
                user.getIdentityStatusVersion(), user.getEmail(), user.getRole(), user.getFullName(),
                user.getPhone(), user.getDob(), user.getAvatarUrl(), user.getAddress(),
                user.getIsActive(), user.getIsBlocked(), user.getBlockedAt(), user.getBlockedBy(),
                user.getBlockReason(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
