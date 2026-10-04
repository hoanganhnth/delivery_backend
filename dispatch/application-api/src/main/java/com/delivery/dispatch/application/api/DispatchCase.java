package com.delivery.dispatch.application.api;

import com.delivery.dispatch.domain.CaseHistory;
import com.delivery.dispatch.domain.DispatchStatus;

/** One locked coordination case as seen by use cases. */
public interface DispatchCase {

    long orderId();

    DispatchStatus status();

    void transitionTo(DispatchStatus status);

    /** Stamps the completion time of a terminal case. */
    void markCompleted();

    CaseHistory history();

    /** Appends one fact to the case history. */
    void record(String stepName, String eventType, String eventData);
}
