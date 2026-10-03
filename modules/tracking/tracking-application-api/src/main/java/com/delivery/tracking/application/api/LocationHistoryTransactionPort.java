package com.delivery.tracking.application.api;
import java.util.function.Supplier;
public interface LocationHistoryTransactionPort {
    <T> T required(Supplier<T> operation);
    <T> T readOnly(Supplier<T> operation);
}
