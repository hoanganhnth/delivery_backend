package com.delivery.match_service.service.impl;

import com.delivery.match_service.service.SettlementEligibilityClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class SettlementEligibilityClientImpl implements SettlementEligibilityClient {

    private final WebClient webClient;

    public SettlementEligibilityClientImpl(
            @Qualifier("settlementServiceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<Boolean> isCodEligible(Long shipperId, BigDecimal codAmount) {
        return webClient.get()
                .uri(uri -> uri.path("/api/settlement/internal/shippers/{shipperId}/cod-eligibility")
                        .queryParam("codAmount", codAmount.toPlainString())
                        .build(shipperId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.createException().flatMap(Mono::error))
                .bodyToMono(new ParameterizedTypeReference<InternalBaseResponse<Boolean>>() {
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("Settlement eligibility response is empty")))
                .flatMap(response -> {
                    if (response.status() != 1 || response.data() == null) {
                        return Mono.error(new IllegalStateException("Invalid settlement eligibility response"));
                    }
                    return Mono.just(response.data());
                });
    }
}
