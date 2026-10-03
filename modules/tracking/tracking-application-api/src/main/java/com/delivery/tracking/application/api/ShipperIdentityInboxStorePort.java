package com.delivery.tracking.application.api;
import java.util.Optional;
import java.util.UUID;
/** Receipt and mapping writes commit together; serialize by event and principal before reading. */
public interface ShipperIdentityInboxStorePort {
    void atomically(UUID eventId, Long principalId, Runnable operation);
    Optional<ShipperIdentityReceipt> receipt(UUID eventId);
    Optional<ShipperIdentityMapping> mapping(Long principalId);
    void saveMapping(ShipperIdentityMapping mapping);
    void saveReceipt(ShipperIdentityReceipt receipt);
}
