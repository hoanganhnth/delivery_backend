package com.delivery.delivery_service.service;

import com.delivery.delivery_service.dto.event.ShipperFoundEvent;
import com.delivery.delivery_service.entity.DeliveryBatchItem;
import com.delivery.delivery_service.exception.InvalidStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates the authoritative global stop numbering for a delivery batch. */
public final class DeliveryBatchRouteValidator {

    private DeliveryBatchRouteValidator() {
    }

    /**
     * A batch of {@code n} items owns exactly the stop positions
     * {@code 0..(2*n-1)}. Each item has one unique pickup and one unique
     * drop-off, and pickup must precede its own drop-off.
     */
    public static void validate(List<ShipperFoundEvent.BatchItem> items) {
        if (items == null || items.isEmpty() || items.size() > 3) {
            throw new InvalidStatusException("Batch item count is invalid");
        }
        Set<Long> deliveries = new HashSet<>();
        Set<Long> orders = new HashSet<>();
        Set<Integer> pickups = new HashSet<>();
        Set<Integer> dropoffs = new HashSet<>();
        int stopCount = items.size() * 2;
        for (ShipperFoundEvent.BatchItem item : items) {
            if (item == null || item.getDeliveryId() == null || item.getOrderId() == null
                    || item.getPickupSequence() == null || item.getDropoffSequence() == null
                    || item.getPickupSequence() < 0 || item.getDropoffSequence() < 0
                    || item.getPickupSequence() >= stopCount || item.getDropoffSequence() >= stopCount
                    || item.getPickupSequence() >= item.getDropoffSequence()
                    || !deliveries.add(item.getDeliveryId())
                    || !orders.add(item.getOrderId())
                    || !pickups.add(item.getPickupSequence())
                    || !dropoffs.add(item.getDropoffSequence())) {
                throw new InvalidStatusException("Batch delivery IDs and stop sequences are invalid");
            }
        }
        validateContiguous(pickups, dropoffs, stopCount);
    }

    /** Validates persisted batch items after loading them from the database. */
    public static void validatePersisted(List<DeliveryBatchItem> items) {
        if (items == null || items.isEmpty() || items.size() > 3) {
            throw new InvalidStatusException("Batch item count is invalid");
        }
        Set<Long> deliveries = new HashSet<>();
        Set<Integer> pickups = new HashSet<>();
        Set<Integer> dropoffs = new HashSet<>();
        int stopCount = items.size() * 2;
        for (DeliveryBatchItem item : items) {
            if (item == null || item.getDeliveryId() == null
                    || item.getPickupSequence() < 0 || item.getDropoffSequence() < 0
                    || item.getPickupSequence() >= stopCount || item.getDropoffSequence() >= stopCount
                    || item.getPickupSequence() >= item.getDropoffSequence()
                    || !deliveries.add(item.getDeliveryId())
                    || !pickups.add(item.getPickupSequence())
                    || !dropoffs.add(item.getDropoffSequence())) {
                throw new InvalidStatusException("Persisted batch item sequences are invalid");
            }
        }
        validateContiguous(pickups, dropoffs, stopCount);
    }

    private static void validateContiguous(Set<Integer> pickups, Set<Integer> dropoffs, int stopCount) {
        Set<Integer> all = new HashSet<>(pickups);
        all.addAll(dropoffs);
        if (pickups.size() * 2 != stopCount || dropoffs.size() * 2 != stopCount
                || all.size() != stopCount) {
            throw new InvalidStatusException("Batch route sequences must cover every global stop exactly once");
        }
        for (int sequence = 0; sequence < stopCount; sequence++) {
            if (!all.contains(sequence)) {
                throw new InvalidStatusException("Batch route sequences must be contiguous");
            }
        }
    }
}
