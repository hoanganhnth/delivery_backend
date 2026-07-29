package com.delivery.order_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.exception.ValidationException;

import reactor.core.publisher.Mono;

class CheckoutPreviewMvpPolicyTest {

    @Test
    void couponCodeIsRejectedBeforeRestaurantLookupInCodMvp() {
        WebClient webClient = mock(WebClient.class);
        ShippingFeeCalculationService shippingFeeService = mock(ShippingFeeCalculationService.class);
        CheckoutPreviewService service = new CheckoutPreviewService(
                webClient,
                shippingFeeService,
                "http://restaurant-service:8083",
                "test-secret");

        CheckoutPreviewRequest request = new CheckoutPreviewRequest();
        request.setCouponCode("WELCOME");

        assertThrows(ValidationException.class, () -> service.calculatePreview(request, 21L));
        verifyNoInteractions(webClient, shippingFeeService);
    }

    @Test
    void missingInternalCredentialFailsBeforeRestaurantLookup() {
        WebClient webClient = mock(WebClient.class);
        ShippingFeeCalculationService shippingFeeService = mock(ShippingFeeCalculationService.class);
        CheckoutPreviewService service = new CheckoutPreviewService(
                webClient,
                shippingFeeService,
                "http://restaurant-service:8083",
                "");

        assertThatThrownBy(() -> service.calculatePreview(validRequest(), 21L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("internal credential");
        verifyNoInteractions(webClient, shippingFeeService);
    }

    @Test
    void previewUsesInternalValidationContractAndCanonicalPickupCoordinates() {
        ShippingFeeCalculationService shippingFeeService = mock(ShippingFeeCalculationService.class);
        when(shippingFeeService.calculateShippingFee(
                10.76, 106.66, 10.78, 106.68, new BigDecimal("100000")))
                .thenReturn(new BigDecimal("15000"));

        CheckoutPreviewService service = new CheckoutPreviewService(
                internalValidationWebClient("""
                        {"status":1,"data":{
                          "restaurantInfo":{"restaurantName":"Quán A","restaurantAddress":"123 Đường A",
                            "restaurantPhone":"0900000000","latitude":10.76,"longitude":106.66,"creatorId":11,
                            "isAvailable":true},
                          "itemValidations":[{"menuItemId":5,"menuItemName":"Cơm","actualPrice":50000,
                            "isAvailable":true,"hasEnoughStock":true}]}}
                        """),
                shippingFeeService,
                "http://restaurant-service:8083",
                "test-secret");

        var preview = service.calculatePreview(validRequest(), 21L);

        assertThat(preview.getRestaurantName()).isEqualTo("Quán A");
        assertThat(preview.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getMenuItemName()).isEqualTo("Cơm");
                    assertThat(item.getUnitPrice()).isEqualByComparingTo("50000");
                    assertThat(item.getLineTotal()).isEqualByComparingTo("100000");
                });
        assertThat(preview.getSubtotal()).isEqualByComparingTo("100000");
        assertThat(preview.getShippingFee()).isEqualByComparingTo("15000");
        assertThat(preview.getTotalPrice()).isEqualByComparingTo("115000");
        assertThat(preview.getUnavailableItemIds()).isEmpty();
    }

    @Test
    void missingCanonicalPickupCoordinatesFailClosedInsteadOfZeroShippingFee() {
        ShippingFeeCalculationService shippingFeeService = mock(ShippingFeeCalculationService.class);
        CheckoutPreviewService service = new CheckoutPreviewService(
                internalValidationWebClient("""
                        {"status":1,"data":{
                          "restaurantInfo":{"restaurantName":"Quán A","restaurantAddress":"123 Đường A",
                            "restaurantPhone":"0900000000","creatorId":11,"isAvailable":true},
                          "itemValidations":[{"menuItemId":5,"menuItemName":"Cơm","actualPrice":50000,
                            "isAvailable":true,"hasEnoughStock":true}]}}
                        """),
                shippingFeeService,
                "http://restaurant-service:8083",
                "test-secret");

        assertThatThrownBy(() -> service.calculatePreview(validRequest(), 21L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("tọa độ pickup latitude");
        verifyNoInteractions(shippingFeeService);
    }

    @Test
    void unavailableRestaurantValidationFailsClosed() {
        ShippingFeeCalculationService shippingFeeService = mock(ShippingFeeCalculationService.class);
        CheckoutPreviewService service = new CheckoutPreviewService(
                internalValidationWebClient("""
                        {"status":0,"data":{
                          "restaurantInfo":{"restaurantName":"Quán A","latitude":10.76,"longitude":106.66},
                          "errors":[{"message":"Món ăn Cơm không khả dụng"}],
                          "itemValidations":[{"menuItemId":5,"menuItemName":"Cơm","actualPrice":50000,
                            "isAvailable":false,"hasEnoughStock":true}]}}
                        """),
                shippingFeeService,
                "http://restaurant-service:8083",
                "test-secret");

        assertThatThrownBy(() -> service.calculatePreview(validRequest(), 21L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Món ăn Cơm không khả dụng");
        verifyNoInteractions(shippingFeeService);
    }

    private WebClient internalValidationWebClient(String json) {
        return WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.method()).isEqualTo(HttpMethod.POST);
                    assertThat(request.url().toString())
                            .isEqualTo("http://restaurant-service:8083/api/restaurants/validate/order");
                    assertThat(request.headers().getFirst("Internal-Token")).isEqualTo("test-secret");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(json)
                            .build());
                })
                .build();
    }

    private CheckoutPreviewRequest validRequest() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest();
        request.setRestaurantId(7L);
        request.setDeliveryLat(10.78);
        request.setDeliveryLng(106.68);

        CheckoutPreviewRequest.PreviewItem item = new CheckoutPreviewRequest.PreviewItem();
        item.setMenuItemId(5L);
        item.setQuantity(2);
        request.setItems(List.of(item));
        return request;
    }
}
