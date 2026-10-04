package com.delivery.settlement.application.api.cod;

import com.delivery.settlement.domain.cod.CodHold;
import com.delivery.settlement.domain.ledger.LedgerAccount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All mutations join one transaction; acquisitions preserve the existing pessimistic locks. */
public interface CodCapacityStore {
    Optional<LedgerAccount> lockShipper(Long shipperId);
    void saveReservedCapacity(LedgerAccount account);
    Optional<CodHold> lockByKey(String key);
    Optional<CodHold> lockHold(UUID holdId);
    CodHold saveHold(CodHold hold);
    List<CodHold> lockActiveForDelivery(Long deliveryId);
    List<CodHold> lockExpired(LocalDateTime now, int limit);
}
