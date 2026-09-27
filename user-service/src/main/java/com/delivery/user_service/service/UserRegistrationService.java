package com.delivery.user_service.service;

import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.RegisterUserCommand;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import com.delivery.user.application.api.UserRegistrationUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService implements UserRegistrationUseCase {
    private final ProvisioningTokenVerifier provisioningTokenVerifier;
    private final UserProfileUseCase userProfileUseCase;

    public UserRegistrationService(
            ProvisioningTokenVerifier provisioningTokenVerifier,
            UserProfileUseCase userProfileUseCase) {
        this.provisioningTokenVerifier = provisioningTokenVerifier;
        this.userProfileUseCase = userProfileUseCase;
    }

    @Transactional
    public UserResponse register(UserRegistrationRequest request) {
        return toResponse(register(new RegisterUserCommand(
                request.getProvisioningToken(),
                request.getFullName(),
                request.getPhone(),
                request.getDob(),
                request.getAvatarUrl(),
                request.getAddress())));
    }

    @Override
    @Transactional
    public UserProfileResult register(RegisterUserCommand request) {
        ProvisioningTokenVerifier.ProvisioningIdentity identity =
                provisioningTokenVerifier.verify(request.provisioningToken());

        UserProfileResult user = userProfileUseCase.create(new CreateUserCommand(
                identity.principalId(),
                identity.principalId(),
                identity.email(),
                identity.role(),
                request.fullName(),
                request.phone(),
                request.dob(),
                request.avatarUrl(),
                request.address()));
        return user;
    }

    private UserResponse toResponse(UserProfileResult result) {
        return UserResponse.builder()
                .id(result.id())
                .authId(result.authId())
                .principalId(result.principalId())
                .email(result.email())
                .role(result.role())
                .fullName(result.fullName())
                .phone(result.phone())
                .dob(result.dob())
                .avatarUrl(result.avatarUrl())
                .address(result.address())
                .createdAt(result.createdAt())
                .updatedAt(result.updatedAt())
                .build();
    }
}
