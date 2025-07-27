package com.delivery.saga_orchestrator_service.client;

import static java.sql.DriverManager.println;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.delivery.saga_orchestrator_service.dto.RegisterRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String AUTH_SERVICE_URL = "http://localhost:8081/api/auth";

    public Long createAuthAccount(RegisterRequest request) {
        try {

            println("Creating Auth Account via HTTP...");
            ResponseEntity<Long> response = restTemplate.postForEntity(
                    AUTH_SERVICE_URL + "/register", request, Long.class);
            return response.getBody(); // ✅ Trả về Long
        } catch (Exception e) {
            // Fallback Kafka
            Map<String, String> fallback = new HashMap<>();
            fallback.put("email", request.getEmail());
            fallback.put("password", request.getPassword());
            // kafkaTemplate.send("auth-create", fallback);
            return null;
        }
    }

    public void deleteAuthAccount(String email) {
        try {
            restTemplate.delete(AUTH_SERVICE_URL + "/internal/delete?email=" + email);
        } catch (Exception e) {
            Map<String, String> fallback = new HashMap<>();
            fallback.put("email", email);
            // kafkaTemplate.send("auth-delete", fallback);
        }
    }
}
