package com.delivery.match_service.service;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface SettlementEligibilityClient {
    Mono<Boolean> isCodEligible(Long shipperId, BigDecimal codAmount);
}
