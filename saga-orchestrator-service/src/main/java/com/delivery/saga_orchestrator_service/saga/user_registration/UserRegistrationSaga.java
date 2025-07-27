package com.delivery.saga_orchestrator_service.saga.user_registration;

import org.springframework.stereotype.Component;

import com.delivery.saga_orchestrator_service.client.AuthServiceClient;
import com.delivery.saga_orchestrator_service.client.UserServiceClient;
import com.delivery.saga_orchestrator_service.dto.CreateProfileRequest;
import com.delivery.saga_orchestrator_service.dto.RegisterRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserRegistrationSaga {

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;
    private final UserEventHandler userEventHandler;

    public void start(RegisterRequest request) {

        Long authid = authServiceClient.createAuthAccount(request);
        if (authid == null) {
            System.out.println("❌ Failed to create Auth Account (HTTP + Kafka fallback used)");
            userEventHandler.handleFailure(request.getEmail(), UserRegistrationSteps.CREATE_AUTH_ACCOUNT);
            return;
        }

        CreateProfileRequest createProfileRequest = CreateProfileRequest.builder()
                .authId(authid)
                .email(request.getEmail())
                .role(request.getRole())
                .build();

        boolean userCreated = userServiceClient.createUserProfile(createProfileRequest);
        if (!userCreated) {
            System.out.println("❌ Failed to create User Profile → rolling back Auth Account");
            userEventHandler.handleFailure(request.getEmail(), UserRegistrationSteps.CREATE_USER_PROFILE);
        }

        System.out.println("✅ User registration saga completed (HTTP prioritized)");
    }
}
