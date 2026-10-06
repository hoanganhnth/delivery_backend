package com.delivery.promotion_service.service;

import com.delivery.promotion_service.entity.Voucher;

public final class VoucherLayerResolver {
    private VoucherLayerResolver() {}

    public static VoucherLayer resolve(Voucher voucher) {
        return VoucherLayer.valueOf(com.delivery.promotion.domain.VoucherLayerResolver
                .resolve(VoucherDomainMapper.snapshot(voucher)).name());
    }
}
