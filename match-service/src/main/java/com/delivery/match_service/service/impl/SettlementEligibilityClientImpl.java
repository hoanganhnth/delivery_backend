package com.delivery.match_service.service.impl;

import com.delivery.match_service.service.SettlementEligibilityClient;
import com.delivery.match_service.config.MatchSettlementCircuitBreaker;
import com.delivery.match_service.config.SettlementCallResilienceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;

import java.math.BigDecimal;

@Service
public class SettlementEligibilityClientImpl implements SettlementEligibilityClient {

    private final WebClient webClient;
    private final MatchSettlementCircuitBreaker circuitBreaker;
    private final SettlementCallResilienceProperties resilienceProperties;

    public SettlementEligibilityClientImpl(
            @Qualifier("settlementServiceWebClient") WebClient webClient,
            MatchSettlementCircuitBreaker circuitBreaker,
            SettlementCallResilienceProperties resilienceProperties) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.resilienceProperties = resilienceProperties;
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
                })
                .timeout(java.time.Duration.ofMillis(resilienceProperties.getTimeoutMs()))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker.circuitBreaker()));
    }
}
