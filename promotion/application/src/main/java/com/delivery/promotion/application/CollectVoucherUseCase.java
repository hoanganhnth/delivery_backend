package com.delivery.promotion.application;

import com.delivery.promotion.application.api.CollectionPort;
import com.delivery.promotion.domain.WalletClaimPolicy;
import com.delivery.promotion.domain.WalletVoucherPolicy;

public final class CollectVoucherUseCase {
    public <V> void collect(CollectionPort<V> port) {
        port.validateIdentity();
        V voucher = port.findVoucher();
        var now = port.now();
        WalletVoucherPolicy.requireCollectable(port.snapshot(voucher), now);
        String failure = WalletClaimPolicy.collectionFailure(port.alreadyCollected(voucher));
        if (failure != null) throw port.duplicate(voucher, failure);
        port.save(voucher);
    }
}
