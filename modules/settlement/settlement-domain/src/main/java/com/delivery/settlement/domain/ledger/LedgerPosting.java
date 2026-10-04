package com.delivery.settlement.domain.ledger;

import com.delivery.settlement.domain.EntityType;
import java.math.BigDecimal;

/** One immutable posting; persistence and balance locking belong to the adapter. */
public record LedgerPosting(Long entityId, EntityType entityType, Long orderId,
        Direction direction, Reason reason, BigDecimal amount, String description, Wallet wallet) {
    public enum Direction { CREDIT, DEBIT }
    public enum Reason { ORDER_EARNING, DELIVERY_FEE, DEPOSIT, DEPOSIT_TOPUP, REFUND_RECEIVED, ADJUSTMENT_CREDIT, RELEASE,
        COD_REFUND, PLATFORM_COMMISSION, WITHDRAW, REFUND_ISSUED, PENALTY, ADJUSTMENT_DEBIT, HOLD,
        COD_DEDUCTION, COD_SETTLEMENT, PROMOTION_SUBSIDY }
    public enum Wallet { EARNINGS, DEPOSIT }
}
