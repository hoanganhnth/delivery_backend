package com.delivery.notification.application.api;

import java.util.Optional;

public interface DurableSendPort<R> {
    Optional<StoredNotification<R>> findByKey(String key);

    /** Return only after the PENDING insert/unique-key claim has committed and been reread.
     * A concurrent claimant may return an existing row, which still needs replay validation. */
    StoredNotification<R> createCommitted(SendCommand command);

    /** Adapter owns the later lock transaction. Failure must propagate to the caller. */
    void deliver(SendCommand command, StoredNotification<R> stored);

    /** Preserve the original response snapshot except its SENT status. */
    void markResponseSent(R response);
}
