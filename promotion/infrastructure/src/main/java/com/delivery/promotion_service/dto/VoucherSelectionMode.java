package com.delivery.promotion_service.dto;

/**
 * Controls how checkout chooses voucher layers.
 *
 * AUTO evaluates the complete wallet and chooses the best deterministic
 * combination. MANUAL applies exactly the IDs supplied by the caller.
 */
public enum VoucherSelectionMode {
    AUTO,
    MANUAL
}
