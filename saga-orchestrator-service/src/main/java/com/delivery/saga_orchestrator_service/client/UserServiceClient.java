package com.delivery.saga_orchestrator_service.client;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.delivery.saga_orchestrator_service.dto.CreateProfileRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String USER_SERVICE_URL = "http://localhost:8084/api/users";

    public boolean createUserProfile(CreateProfileRequest request) {
        try {
         
            System.out.println("Creating user profile with authId: " + request.getAuthId());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);


            // Gói cả headers và body vào HttpEntity
            HttpEntity<CreateProfileRequest> entity = new HttpEntity<>(request, headers);

            // Gửi POST request
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    USER_SERVICE_URL, entity, Void.class);  

            // Trả về true nếu status code là 2xx
            System.out.println("Response status code: " + response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();


        } catch (Exception e) {
            System.out.println("❌ Failed to create User Profile via HTTP: " + e.getMessage());
            return false; // Fallback to Kafka
        }
    }
}
