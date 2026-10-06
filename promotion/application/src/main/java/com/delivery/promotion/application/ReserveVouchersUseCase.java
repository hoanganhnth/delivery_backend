package com.delivery.promotion.application;

import com.delivery.promotion.application.api.ReservationPort;
import com.delivery.promotion.domain.WalletClaimPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReserveVouchersUseCase {
    public <S, R, W, V, Q> R reserve(ReservationPort<S, R, W, V, Q> port) {
        var command = port.prepare();
        var sameId = port.findById();
        if (sameId.isPresent()) {
            port.requireExactReplay(sameId.get(), command);
            return port.replayResult(sameId.get());
        }
        var sameOrder = port.findByOrder();
        if (sameOrder.isPresent()) {
            port.requireExactReplay(sameOrder.get(), command);
            return port.replayResult(sameOrder.get());
        }
        Map<Long, W> wallets = new LinkedHashMap<>();
        Map<Long, V> vouchers = new LinkedHashMap<>();
        for (Long id : command.voucherIds()) {
            W wallet = port.lockWallet(id);
            String failure = WalletClaimPolicy.claimFailure(port.walletStatus(wallet));
            if (failure != null) throw port.conflict(failure);
            V voucher = port.lockVoucher(id);
            port.requireCapacity(wallet, voucher);
            wallets.put(id, wallet);
            vouchers.put(id, voucher);
        }
        Q quote = port.quote(vouchers, command);
        return port.persist(wallets, vouchers, quote, command);
    }
}
