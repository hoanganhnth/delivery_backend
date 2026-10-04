package com.delivery.settlement.application.api.ledger;

import com.delivery.settlement.domain.ledger.CompletedCodDelivery;

public interface CodSettlementUseCase {
    enum Outcome { POSTED, REPLAY }
    /** Caller validates isolated simulation context and supplies the existing immutable wire fingerprint. */
    Outcome settle(CompletedCodDelivery delivery, String fingerprint);
}
