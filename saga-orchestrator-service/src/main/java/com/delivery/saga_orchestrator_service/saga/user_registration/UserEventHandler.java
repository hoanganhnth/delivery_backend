package com.delivery.saga_orchestrator_service.saga.user_registration;

import com.delivery.saga_orchestrator_service.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventHandler {

    private final AuthServiceClient authServiceClient;

    public void handleFailure(String email, UserRegistrationSteps failedStep) {
        switch (failedStep) {
            case CREATE_USER_PROFILE -> {
                System.out.println("⏪ Rolling back: DELETE Auth Account for " + email);
                authServiceClient.deleteAuthAccount(email); // rollback thật sự
            }
            default -> System.out.println("⚠️ Unhandled failure step: " + failedStep);
        }
    }
}
