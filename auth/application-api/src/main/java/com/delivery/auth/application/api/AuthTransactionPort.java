package com.delivery.auth.application.api;

public interface AuthTransactionPort {
    <T> T required(java.util.function.Supplier<T> operation);
}
