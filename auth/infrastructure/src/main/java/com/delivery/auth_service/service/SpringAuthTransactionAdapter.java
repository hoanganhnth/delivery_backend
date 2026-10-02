package com.delivery.auth_service.service;

import com.delivery.auth.application.api.AuthTransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringAuthTransactionAdapter implements AuthTransactionPort {
    private final TransactionTemplate transactions;
    public SpringAuthTransactionAdapter(PlatformTransactionManager manager) {
        transactions = new TransactionTemplate(manager);
    }
    @Override public <T> T required(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
