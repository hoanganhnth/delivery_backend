package com.delivery.analytics.applicationapi;

import com.delivery.analytics.domain.OrderReconciliationAccumulator.Snapshot;
import java.time.LocalDate;

public interface ReconciliationUseCase {
    Result reconcile(LocalDate date);
    record Result(long processed, Snapshot platform, int restaurants) {}
}
