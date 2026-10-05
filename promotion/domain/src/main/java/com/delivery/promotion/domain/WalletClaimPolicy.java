package com.delivery.promotion.domain;

/** Claim fences; retains the historical bulk-only per-user limit and count normalization. */
public final class WalletClaimPolicy {
    private WalletClaimPolicy() {}

    public static String collectionFailure(boolean alreadyCollected) {
        return alreadyCollected ? "Voucher already collected" : null;
    }

    public static String claimFailure(String status) {
        return "SAVED".equals(status) ? null : "Voucher is already reserved or used";
    }

    public static String capacityFailure(Integer usedCount, Integer reservedCount, Integer usageLimit) {
        int used = usedCount == null ? 0 : Math.max(0, usedCount);
        int reserved = reservedCount == null ? 0 : Math.max(0, reservedCount);
        int limit = usageLimit == null ? 1 : usageLimit;
        return used + reserved >= limit ? "Voucher usage limit has been reached" : null;
    }
}
