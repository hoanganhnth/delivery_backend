package com.delivery.user_service.service;

import com.delivery.user_service.dto.AuthProvisioningIdentityResponse;
import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import org.springframework.stereotype.Service;

@Service
public class UserRegistrationService {
    private final AuthRegistrationClient authRegistrationClient;
    private final UserService userService;

    public UserRegistrationService(
            AuthRegistrationClient authRegistrationClient,
            UserService userService) {
        this.authRegistrationClient = authRegistrationClient;
        this.userService = userService;
    }

    public UserResponse register(UserRegistrationRequest request) {
        AuthProvisioningIdentityResponse identity =
                authRegistrationClient.resolve(request.getProvisioningToken());

        UserRequest trustedRequest = UserRequest.builder()
                .authId(identity.getAuthId())
                .email(identity.getEmail())
                .role(identity.getRole())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .dob(request.getDob())
                .avatarUrl(request.getAvatarUrl())
                .address(request.getAddress())
                .build();
        UserResponse user = userService.createUser(trustedRequest);
        authRegistrationClient.complete(request.getProvisioningToken(), user.getId());
        return user;
    }
}
