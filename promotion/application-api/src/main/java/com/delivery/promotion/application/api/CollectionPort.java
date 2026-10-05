package com.delivery.promotion.application.api;

import com.delivery.promotion.domain.Voucher;
import java.time.LocalDateTime;

/** Called within the host transaction; save must flush and retain duplicate-race translation. */
public interface CollectionPort<V> {
    void validateIdentity();
    V findVoucher();
    Voucher snapshot(V voucher);
    LocalDateTime now();
    boolean alreadyCollected(V voucher);
    RuntimeException duplicate(V voucher, String message);
    void save(V voucher);
}
