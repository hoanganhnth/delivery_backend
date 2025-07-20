package com.delivery.saga_orchestrator_service.saga.user_registration;

import com.delivery.saga_orchestrator_service.client.AuthServiceClient;
import com.delivery.saga_orchestrator_service.client.UserServiceClient;
import com.delivery.saga_orchestrator_service.dto.RegisterRequest;

public class UserRegistrationSaga {

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;

    // contructor injection
    public UserRegistrationSaga(AuthServiceClient authServiceClient, UserServiceClient userServiceClient) {
        this.authServiceClient = authServiceClient;
        this.userServiceClient = userServiceClient;
    }


    public void start(RegisterRequest request) {
        try {
            // Step 1: Gọi AuthService tạo tài khoản xác thực
            authServiceClient.createAuthAccount(request.getEmail(), request.getPassword());

            // Step 2: Gọi UserService tạo thông tin người dùng
            userServiceClient.createUserProfile(request.getEmail(), request.getUsername());

        } catch (Exception e) {
            // Nếu Step 2 fail → rollback Step 1
            authServiceClient.deleteAuthAccount(request.getEmail());
        }
    }
}
