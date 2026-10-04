package com.delivery.settlement.domain.ledger;

import java.util.List;

/** COD capacity is consumed after cash settlement and before the final commission. */
public record CodSettlementPlan(List<LedgerPosting> beforeHoldConsumption, LedgerPosting platformCommission) {
    public CodSettlementPlan {
        beforeHoldConsumption = List.copyOf(beforeHoldConsumption);
    }
}
