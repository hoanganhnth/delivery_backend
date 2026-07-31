package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutReservationClientContractTest {

    @Test
    void voucherReserveUsesExplicitServicePortInternalCredentialAndStableIdentity() {
        UUID reservationId = UUID.randomUUID();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.method()).isEqualTo(HttpMethod.POST);
            assertThat(request.url().toString())
                    .isEqualTo("http://promotion-service:8096/api/promotions/reserve");
            assertThat(request.headers().getFirst("Internal-Token")).isEqualTo("test-secret");
            return json("""
                    {"status":1,"data":{"reservationId":"%s","orderId":41,
                    "state":"RESERVED","discountAmount":10000}}
                    """.formatted(reservationId));
        }).build();

        CheckoutReservationClient client = client(webClient);
        CheckoutReservationClient.VoucherQuote quote = client.reserveVoucher(
                reservationId, 41L, 21L, 31L, 11L,
                new BigDecimal("45000"), new BigDecimal("15000"));

        assertThat(quote.discountAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void flashReserveAndReleaseUseExplicitServicePortAndSameIdentity() {
        UUID reservationId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.method()).isEqualTo(HttpMethod.POST);
            assertThat(request.headers().getFirst("Internal-Token")).isEqualTo("test-secret");
            if (calls.getAndIncrement() == 0) {
                assertThat(request.url().toString())
                        .isEqualTo("http://flashsale-service:8092/api/flashsales/internal/reserve");
                return json("""
                        {"status":1,"data":{"reservationId":"%s","orderId":41,
                        "state":"RESERVED","items":[{"flashSaleItemId":71,"menuItemId":51,
                        "quantity":1,"unitPrice":30000}]}}
                        """.formatted(reservationId));
            }
            assertThat(request.url().toString()).isEqualTo(
                    "http://flashsale-service:8092/api/flashsales/internal/reservations/"
                            + reservationId + "/release?orderId=41");
            return json("{\"status\":1,\"data\":{}}");
        }).build();

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(51L);
        item.setFlashSaleItemId(71L);
        item.setQuantity(1);
        CheckoutReservationClient client = client(webClient);

        CheckoutReservationClient.FlashQuote quote = client.reserveFlash(
                reservationId, 41L, 21L, 11L, List.of(item));
        client.releaseFlash(reservationId, 41L);

        assertThat(quote.byFlashSaleItemId().get(71L).unitPrice())
                .isEqualByComparingTo("30000");
        assertThat(calls).hasValue(2);
    }

    private CheckoutReservationClient client(WebClient webClient) {
        return new CheckoutReservationClient(webClient,
                "http://promotion-service:8096", "http://flashsale-service:8092",
                "test-secret", 2000);
    }

    private Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build());
    }
}
