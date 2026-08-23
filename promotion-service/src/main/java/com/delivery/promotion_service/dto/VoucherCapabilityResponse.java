package com.delivery.promotion_service.dto;

import java.util.List;

public record VoucherCapabilityResponse(
        boolean enabled,
        int maxVouchers,
        List<String> layers,
        List<String> selectionModes,
        boolean conflictsWithFlashSale) {
}
