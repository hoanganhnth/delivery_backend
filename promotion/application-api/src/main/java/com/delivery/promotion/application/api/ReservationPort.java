package com.delivery.promotion.application.api;

import java.util.Map;
import java.util.Optional;

/** Ordered reservation effects in the caller's transaction. No lock may be deferred past quote. */
public interface ReservationPort<S, R, W, V, Q> {
    PromotionCommands.Reserve prepare();
    Optional<S> findById();
    Optional<S> findByOrder();
    void requireExactReplay(S reservation, PromotionCommands.Reserve command);
    R replayResult(S reservation);
    W lockWallet(Long voucherId);
    String walletStatus(W wallet);
    V lockVoucher(Long voucherId);
    void requireCapacity(W wallet, V voucher);
    Q quote(Map<Long, V> vouchers, PromotionCommands.Reserve command);
    R persist(Map<Long, W> wallets, Map<Long, V> vouchers, Q quote, PromotionCommands.Reserve command);
    RuntimeException conflict(String message);
}
