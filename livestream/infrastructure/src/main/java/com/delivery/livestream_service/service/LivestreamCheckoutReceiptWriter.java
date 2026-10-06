package com.delivery.livestream_service.service;

import com.delivery.livestream_service.entity.LivestreamCheckoutReceipt;
import com.delivery.livestream_service.repository.LivestreamCheckoutReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates a unique-key race so the caller can safely load the winning receipt. */
@Service
public class LivestreamCheckoutReceiptWriter {
    private final LivestreamCheckoutReceiptRepository receipts;

    public LivestreamCheckoutReceiptWriter(LivestreamCheckoutReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(LivestreamCheckoutReceipt receipt) {
        receipts.saveAndFlush(receipt);
    }
}
