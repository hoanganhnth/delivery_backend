package com.delivery.saga_orchestrator_service.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String role;

    // ✅ Trường mới để dùng trong phản hồi từ Kafka
    private boolean success;
}
