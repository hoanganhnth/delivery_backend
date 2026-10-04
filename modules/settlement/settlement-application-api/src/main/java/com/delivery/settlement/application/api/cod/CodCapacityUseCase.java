package com.delivery.settlement.application.api.cod;

import com.delivery.settlement.domain.cod.*;
import java.util.List;
import java.util.UUID;

public interface CodCapacityUseCase {
    int EXPIRY_SCAN_LIMIT = 200;
    List<CodHold> hold(CodHoldCommand command);
    CodHold transition(UUID holdId, CodHold.Status target);
    void consumeForDelivery(Long deliveryId);
    void expireDueHolds();
}
