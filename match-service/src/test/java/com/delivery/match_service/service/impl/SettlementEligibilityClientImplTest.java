package com.delivery.match_service.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementEligibilityClientImplTest {

    @Test
    void returnsCanonicalBooleanData() {
        assertThat(clientWithJson("{\"status\":1,\"message\":\"Eligible\",\"data\":true}")
                .isCodEligible(10L, new BigDecimal("100000"))
                .block()).isTrue();
        assertThat(clientWithJson("{\"status\":1,\"message\":\"Denied\",\"data\":false}")
                .isCodEligible(10L, new BigDecimal("100000"))
                .block()).isFalse();
    }

    @Test
    void rejectsFailureAndNullDataEnvelopes() {
        assertInvalid("{\"status\":0,\"message\":\"Failure\",\"data\":true}");
        assertInvalid("{\"status\":1,\"message\":\"Malformed\",\"data\":null}");
    }

    @Test
    void rejectsEmptyBody() {
        SettlementEligibilityClientImpl client = clientWithResponse(
                ClientResponse.create(HttpStatus.OK).build());

        assertThatThrownBy(() -> client.isCodEligible(10L, BigDecimal.ONE).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> clientWithJson(json)
                .isCodEligible(10L, BigDecimal.ONE)
                .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid settlement eligibility response");
    }

    private SettlementEligibilityClientImpl clientWithJson(String json) {
        return clientWithResponse(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
    }

    private SettlementEligibilityClientImpl clientWithResponse(ClientResponse response) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://settlement-service")
                .exchangeFunction(request -> Mono.just(response))
                .build();
        return new SettlementEligibilityClientImpl(webClient);
    }
}
