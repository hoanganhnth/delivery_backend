package com.delivery.user_service.service;

import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UpdateProfileCommand;
import com.delivery.user.application.api.UserProfilePort;
import com.delivery.user.application.api.UserProvisioningIdentityCheck;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Atomic profile persistence and outbox publication. Identity rules are supplied by core. */
@Component
@RequiredArgsConstructor
public class JpaUserProfileAdapter implements UserProfilePort {

    private final UserRepository users;
    private final IdentityOutboxService identityOutbox;

    @Override
    @Transactional
    public UserProfileResult create(CreateUserCommand command, UserProvisioningIdentityCheck identityCheck) {
        User current = users.findByPrincipalId(command.principalId())
                .map(existing -> {
                    identityCheck.existingPrincipal(toResult(existing));
                    return existing;
                })
                .orElseGet(() -> provision(command, identityCheck));
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

    private User provision(CreateUserCommand command, UserProvisioningIdentityCheck identityCheck) {
        users.findByEmailIgnoreCase(command.email())
                .ifPresent(existing -> identityCheck.existingEmail(toResult(existing)));
        users.insertProvisionedUserIfAbsent(
                command.authId(), command.principalId(), command.email(), command.role(),
                command.fullName(), command.phone(), command.dob(), command.avatarUrl(), command.address());
        User winner = users.findByPrincipalId(command.principalId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "User provisioning could not be completed"));
        identityCheck.existingPrincipal(toResult(winner));
        return winner;
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
