package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import com.delivery.order_service.entity.CheckoutQuote;
import com.delivery.order_service.repository.CheckoutQuoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Persists a price confirmation independently from a rejected create command. */
@Service
public class CheckoutQuoteIssuer {
    private final CheckoutPreviewService previewService;
    private final CheckoutFingerprintService fingerprints;
    private final CheckoutQuoteRepository repository;
    private final Clock clock;
    private final Duration ttl;

    public CheckoutQuoteIssuer(CheckoutPreviewService previewService,
                               CheckoutFingerprintService fingerprints,
                               CheckoutQuoteRepository repository,
                               Clock clock,
                               @Value("${app.order.quote.ttl:PT5M}") Duration ttl) {
        this.previewService = previewService;
        this.fingerprints = fingerprints;
        this.repository = repository;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutPreviewResponse issue(CheckoutPreviewRequest request, Long principalId, Long userId) {
        CheckoutPreviewResponse response = previewService.calculatePreview(request, principalId, userId);
        return persist(request, response, principalId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutPreviewResponse persist(CheckoutPreviewRequest request, CheckoutPreviewResponse response,
                                           Long principalId) {
        Instant now = clock.instant();
        UUID quoteId = UUID.randomUUID();
        Instant expiresAt = now.plus(ttl);
        repository.save(new CheckoutQuote(quoteId, principalId,
                fingerprints.pricingInput(request), fingerprints.pricingSnapshot(response), expiresAt, now));
        response.setQuoteId(quoteId);
        response.setExpiresAt(expiresAt);
        return response;
    }
}
