package com.delivery.settlement.domain.ledger;

import com.delivery.settlement.domain.EntityType;

public record LedgerOwner(Long entityId, EntityType entityType) {}
