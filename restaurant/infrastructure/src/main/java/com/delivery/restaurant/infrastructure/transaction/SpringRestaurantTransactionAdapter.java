package com.delivery.restaurant.infrastructure.transaction;

import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringRestaurantTransactionAdapter implements RestaurantTransactionPort {
    private final TransactionTemplate transactions;
    private final TransactionTemplate reads;
    public SpringRestaurantTransactionAdapter(PlatformTransactionManager manager) {
        transactions = new TransactionTemplate(manager);
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
    }
    @Override public <T> T readOnly(Supplier<T> operation) {
        return reads.execute(status -> operation.get());
    }
    @Override public <T> T required(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
