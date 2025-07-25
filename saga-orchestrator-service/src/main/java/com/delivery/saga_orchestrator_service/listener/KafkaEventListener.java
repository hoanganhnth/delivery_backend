// package com.delivery.saga_orchestrator_service.listener;

// import com.delivery.saga_orchestrator_service.dto.RegisterRequest;
// import com.delivery.saga_orchestrator_service.saga.user_registration.UserEventHandler;
// import com.delivery.saga_orchestrator_service.saga.user_registration.UserRegistrationSteps;
// import org.apache.kafka.clients.consumer.ConsumerRecord;
// import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.stereotype.Component;

// @Component
// public class KafkaEventListener {

//     private final UserEventHandler userEventHandler;

//     public KafkaEventListener(UserEventHandler userEventHandler) {
//         this.userEventHandler = userEventHandler;
//     }

//     @KafkaListener(topics = "auth-create-response", groupId = "saga")
//     public void handleAuthCreateResponse(ConsumerRecord<String, RegisterRequest> record) {
//         RegisterRequest event = record.value();
//         if (!event.isSuccess()) {
//             userEventHandler.handleFailure(event.getEmail(), UserRegistrationSteps.CREATE_AUTH_ACCOUNT);
//         }
//     }

//     @KafkaListener(topics = "user-create-response", groupId = "saga")
//     public void handleUserCreateResponse(ConsumerRecord<String, RegisterRequest> record) {
//         RegisterRequest event = record.value();
//         if (!event.isSuccess()) {
//             userEventHandler.handleFailure(event.getEmail(), UserRegistrationSteps.CREATE_USER_PROFILE);
//         }
//     }
// }
