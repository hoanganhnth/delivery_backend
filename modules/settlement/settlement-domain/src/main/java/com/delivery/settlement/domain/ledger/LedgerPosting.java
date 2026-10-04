package com.delivery.settlement.domain.ledger;

import com.delivery.settlement.domain.EntityType;
import java.math.BigDecimal;

/** One immutable posting; persistence and balance locking belong to the adapter. */
public record LedgerPosting(Long entityId, EntityType entityType, Long orderId,
        Direction direction, Reason reason, BigDecimal amount, String description, Wallet wallet) {
    public enum Direction { CREDIT, DEBIT }
    public enum Reason { ORDER_EARNING, PROMOTION_SUBSIDY, DELIVERY_FEE, COD_SETTLEMENT, PLATFORM_COMMISSION }
    public enum Wallet { EARNINGS, DEPOSIT }
}
