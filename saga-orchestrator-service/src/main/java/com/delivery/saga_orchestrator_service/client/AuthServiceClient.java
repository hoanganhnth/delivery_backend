package com.delivery.saga_orchestrator_service.client;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuthServiceClient {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuthServiceClient(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void createAuthAccount(String email, String password) {
        Map<String, String> message = new HashMap<>();
        message.put("email", email);
        message.put("password", password);

        kafkaTemplate.send("auth-create", message);
    }

    public void deleteAuthAccount(String email) {
        Map<String, String> message = new HashMap<>();
        message.put("email", email);

        kafkaTemplate.send("auth-delete", message);
    }
}
