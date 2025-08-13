package com.delivery.saga_orchestrator_service.saga.order;


import org.springframework.stereotype.Component;

import com.delivery.saga_orchestrator_service.client.DeliveryServiceClient;
import com.delivery.saga_orchestrator_service.client.OrderServiceClient;
import com.delivery.saga_orchestrator_service.client.ShipperServiceClient;
import com.delivery.saga_orchestrator_service.dto.order.CreateOrderRequest;
import com.delivery.saga_orchestrator_service.dto.order.OrderResponse;

@Component
public class OrderSaga {
    private final OrderServiceClient orderServiceClient;
    // private final ShipperServiceClient shipperServiceClient;
    // private final DeliveryServiceClient deliveryServiceClient;

    // constructor injection
    public OrderSaga(OrderServiceClient orderServiceClient, ShipperServiceClient shipperServiceClient,
            DeliveryServiceClient deliveryServiceClient) {
        this.orderServiceClient = orderServiceClient;
        // this.shipperServiceClient = shipperServiceClient;
        // this.deliveryServiceClient = deliveryServiceClient;

    }

    public void executeOrderCreationSaga(CreateOrderRequest request, Long userId, String role) {
        try {
            System.out.println("🚀 Step 1: Creating order...");
            OrderResponse orderResponse = orderServiceClient.createOrder(request, userId, role);
            if (orderResponse == null) {
                throw new Exception("Failed to create order");
            }
            System.out.println("✅ Order created successfully with ID: " + orderResponse.getId());
            System.out.println("🔍 Step 2: Finding available shippers...");

            // deliveryServiceClient.findNearbyShippers(
            //         orderResponse.getRestaurantLat(),
            //         orderResponse.getRestaurantLng(),
            //         userId, role);

        } catch (Exception e) {
            System.out.println("❌ Order creation saga failed: " + e.getMessage());
        }

    }
}
