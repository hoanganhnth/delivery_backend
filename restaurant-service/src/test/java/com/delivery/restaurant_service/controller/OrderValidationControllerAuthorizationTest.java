package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.dto.request.OrderValidationRequest;
import com.delivery.restaurant_service.dto.response.OrderValidationResultResponse;
import com.delivery.restaurant_service.service.OrderCacheValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderValidationControllerAuthorizationTest {

    @Mock
    private OrderCacheValidationService validationService;

    private OrderValidationController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderValidationController(validationService);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
    }

    @Test
    void missingInternalTokenIsRejectedWithoutValidation() {
        var response = controller.validateOrder(new OrderValidationRequest(), null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(validationService);
    }

    @Test
    void wrongInternalTokenIsRejectedWithoutValidation() {
        var response = controller.validateOrder(new OrderValidationRequest(), "wrong-secret");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(validationService);
    }

    @Test
    void matchingInternalTokenAllowsValidation() {
        OrderValidationRequest request = OrderValidationRequest.builder().restaurantId(7L).build();
        OrderValidationResultResponse result = OrderValidationResultResponse.builder()
                .isValid(true)
                .build();
        when(validationService.validateOrderFromOrderService(request)).thenReturn(result);

        var response = controller.validateOrder(request, "test-secret");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getStatus());
    }
}
