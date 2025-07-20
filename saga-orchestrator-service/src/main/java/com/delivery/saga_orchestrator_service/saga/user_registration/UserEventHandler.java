package com.delivery.saga_orchestrator_service.saga.user_registration;


public class UserEventHandler {
    public void handleFailure(String email, UserRegistrationSteps failedStep) {
        switch (failedStep) {
            case CREATE_USER_PROFILE:
                // Rollback bước trước đó
                // Ví dụ rollback AuthAccount
                System.out.println("Rolling back: DELETE Auth Account for " + email);
                break;
            default:
                System.out.println("Unhandled failure step: " + failedStep);
        }
    }
}