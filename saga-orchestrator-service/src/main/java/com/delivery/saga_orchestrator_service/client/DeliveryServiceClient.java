package com.delivery.saga_orchestrator_service.client;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import com.delivery.saga_orchestrator_service.common.constants.HttpHeaderConstants;
import com.delivery.saga_orchestrator_service.common.constants.KafkaTopicConstants;

public class DeliveryServiceClient {
    private static final String DELIVERY_SERVICE_URL = "http://localhost:8085/api/deliveries";
    private final RestTemplate restTemplate = new RestTemplate();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryServiceClient(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

}
