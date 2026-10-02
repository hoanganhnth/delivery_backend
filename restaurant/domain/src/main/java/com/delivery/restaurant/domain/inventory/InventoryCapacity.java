package com.delivery.restaurant.domain.inventory;

/** Immutable stock arithmetic; persistence and row locking are external concerns. */
public record InventoryCapacity(Integer onHandQuantity, Integer reservedQuantity, Long revision) {
    public int availableQuantity() { return Math.max(0, onHandQuantity - reservedQuantity); }

    public boolean canReserve(int quantity) {
        return onHandQuantity != null && reservedQuantity != null
                && onHandQuantity >= reservedQuantity && availableQuantity() >= quantity;
    }

    public InventoryCapacity reserve(int quantity, Long menuItemId) {
        if (!canReserve(quantity)) {
            throw new IllegalArgumentException("Insufficient inventory for menu item " + menuItemId);
        }
        return new InventoryCapacity(onHandQuantity, Math.addExact(reservedQuantity, quantity), nextRevision());
    }

    public void requireCommit(int quantity) {
        if (reservedQuantity < quantity || onHandQuantity < quantity) {
            throw new IllegalStateException("Inventory ledger is inconsistent");
        }
    }

    public InventoryCapacity commit(int quantity) {
        requireCommit(quantity);
        return new InventoryCapacity(onHandQuantity - quantity, reservedQuantity - quantity, nextRevision());
    }

    public void requireRelease(int quantity) {
        if (reservedQuantity < quantity) throw new IllegalStateException("Inventory ledger is inconsistent");
    }

    public InventoryCapacity release(int quantity) {
        requireRelease(quantity);
        return new InventoryCapacity(onHandQuantity, reservedQuantity - quantity, nextRevision());
    }

    public InventoryCapacity restoreCommitted(int quantity) {
        return new InventoryCapacity(Math.addExact(onHandQuantity, quantity), reservedQuantity, nextRevision());
    }

    public InventoryCapacity updateOnHand(int quantity, Long expectedRevision) {
        if (expectedRevision == null || !expectedRevision.equals(revision)) {
            throw new IllegalArgumentException("Inventory revision is stale; reload before updating");
        }
        if (quantity < reservedQuantity) {
            throw new IllegalArgumentException("onHandQuantity cannot be below reserved quantity");
        }
        return new InventoryCapacity(quantity, reservedQuantity, nextRevision());
    }

    private long nextRevision() { return Math.addExact(revision, 1L); }
}
