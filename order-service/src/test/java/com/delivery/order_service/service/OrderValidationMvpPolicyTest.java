package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.exception.ValidationException;
import com.delivery.order_service.config.OrderRestaurantCircuitBreaker;
import com.delivery.order_service.config.RestaurantCallResilienceProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderValidationMvpPolicyTest {

    @Mock
    private WebClient webClient;

    @Test
    void onlinePaymentIsRejectedBeforeCallingRestaurantService() {
        CreateOrderRequest request = validCodRequest();
        request.setPaymentMethod("ONLINE");

        OrderValidationService service = new OrderValidationService(
                webClient,
                "http://restaurant-service:8083",
                "test-secret", circuitBreaker());

        assertThrows(ValidationException.class,
                () -> service.validateCreateOrderRequest(request, 21L));
        verifyNoInteractions(webClient);
    }

    @Test
    void voucherCheckoutIsClosedUntilDiscountAndCompensationAreProven() {
        CreateOrderRequest request = validCodRequest();
        request.setVoucherIds(List.of(3L));

        OrderValidationService service = new OrderValidationService(
                webClient,
                "http://restaurant-service:8083",
                "test-secret", circuitBreaker());

        assertThrows(ValidationException.class,
                () -> service.validateCreateOrderRequest(request, 21L));
        verifyNoInteractions(webClient);
    }

    @Test
    void flashSaleCheckoutIsClosedUntilReservationIsRecoverable() {
        CreateOrderRequest request = validCodRequest();
        request.getItems().get(0).setFlashSaleItemId(4L);

        OrderValidationService service = new OrderValidationService(
                webClient,
                "http://restaurant-service:8083",
                "test-secret", circuitBreaker());

        assertThrows(ValidationException.class,
                () -> service.validateCreateOrderRequest(request, 21L));
        verifyNoInteractions(webClient);
    }

    private OrderRestaurantCircuitBreaker circuitBreaker() {
        return new OrderRestaurantCircuitBreaker(new RestaurantCallResilienceProperties(), new SimpleMeterRegistry());
    }

    private CreateOrderRequest validCodRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(7L);
        request.setDeliveryAddress("123 Test");
        request.setDeliveryLat(10.8);
        request.setDeliveryLng(106.7);
        request.setCustomerName("Khách hàng");
        request.setCustomerPhone("0901234567");
        request.setPaymentMethod("COD");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(9L);
        item.setQuantity(1);
        request.setItems(List.of(item));
        return request;
    }
}
