package com.delivery.saga_orchestrator_service.client;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserServiceClient {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserServiceClient(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    public void createUserProfile(String email, String name) {
        // Gửi message tới topic "user-create"
        Map<String, String> message = new HashMap<>();
        message.put("email", email);
        message.put("name", name);

        kafkaTemplate.send("user-create", message);
    }
}