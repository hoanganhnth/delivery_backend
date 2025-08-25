// package com.delivery.notification_service.listener;

// import com.delivery.notification_service.common.constants.KafkaTopicConstants;
// import com.delivery.notification_service.dto.event.DeliveryEvent;
// import com.delivery.notification_service.service.NotificationService;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.kafka.support.KafkaHeaders;
// import org.springframework.messaging.handler.annotation.Header;
// import org.springframework.messaging.handler.annotation.Payload;
// import org.springframework.stereotype.Component;

// /**
//  * ✅ Delivery Event Listener để nhận events từ Delivery Service theo Backend Instructions
//  * Chỉ xử lý delivery status updates - shipper matching được xử lý bởi MatchEventListener
//  */
// @Slf4j
// @Component
// public class DeliveryEventListener {

//     private final NotificationService notificationService;

//     public DeliveryEventListener(NotificationService notificationService) {
//         this.notificationService = notificationService;
//     }

//     @KafkaListener(topics = KafkaTopicConstants.DELIVERY_STATUS_UPDATED_TOPIC)
//     public void handleDeliveryStatusUpdatedEvent(
//             @Payload DeliveryEvent event,
//             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
//             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
//             @Header(KafkaHeaders.OFFSET) String offset) {

//         log.info("📥 Received DeliveryStatusUpdatedEvent: deliveryId={}, orderId={}, userId={}, status={}",
//                 event.getDeliveryId(), event.getOrderId(), event.getUserId(), event.getStatus());

//         try {
//             // Send delivery status notification to user
//             notificationService.sendDeliveryStatusNotification(
//                     event.getUserId(),
//                     event.getDeliveryId(),
//                     event.getStatus(),
//                     event.getShipperName()
//             );

//             log.info("✅ Successfully processed DeliveryStatusUpdatedEvent for delivery: {}", event.getDeliveryId());

//         } catch (Exception e) {
//             log.error("💥 Failed to process DeliveryStatusUpdatedEvent for delivery {}: {}", event.getDeliveryId(), e.getMessage(), e);
//         }
//     }

//     // NOTE: Shipper assignment notifications đã được chuyển sang MatchEventListener
//     // Match Service sẽ gọi đến notification để thông báo cho shipper phù hợp
// }
