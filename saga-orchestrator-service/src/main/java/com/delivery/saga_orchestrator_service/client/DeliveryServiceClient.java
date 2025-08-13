package com.delivery.saga_orchestrator_service.client;


import org.springframework.kafka.core.KafkaTemplate;
// import org.springframework.web.client.RestTemplate;


public class DeliveryServiceClient {
    // private static final String DELIVERY_SERVICE_URL = "http://localhost:8085/api/deliveries";
    // private final RestTemplate restTemplate = new RestTemplate();
    // private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryServiceClient(KafkaTemplate<String, Object> kafkaTemplate) {
        // this.kafkaTemplate = kafkaTemplate;
    }

}
