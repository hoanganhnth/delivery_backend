package com.delivery.user_service.service;

import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {
    private final ProvisioningTokenVerifier provisioningTokenVerifier;
    private final UserService userService;

    public UserRegistrationService(
            ProvisioningTokenVerifier provisioningTokenVerifier,
            UserService userService) {
        this.provisioningTokenVerifier = provisioningTokenVerifier;
        this.userService = userService;
    }

    @Transactional
    public UserResponse register(UserRegistrationRequest request) {
        ProvisioningTokenVerifier.ProvisioningIdentity identity =
                provisioningTokenVerifier.verify(request.getProvisioningToken());

        UserRequest trustedRequest = UserRequest.builder()
                .authId(identity.principalId())
                .principalId(identity.principalId())
                .email(identity.email())
                .role(identity.role())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .dob(request.getDob())
                .avatarUrl(request.getAvatarUrl())
                .address(request.getAddress())
                .build();
        UserResponse user = userService.createUser(trustedRequest);
        return user;
    }
}
