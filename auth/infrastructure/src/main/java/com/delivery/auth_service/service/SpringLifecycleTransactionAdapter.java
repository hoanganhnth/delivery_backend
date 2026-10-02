package com.delivery.auth_service.service;
import com.delivery.auth.application.api.LifecycleTransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
@Component
public final class SpringLifecycleTransactionAdapter implements LifecycleTransactionPort {
    private final TransactionTemplate transactions;
    public SpringLifecycleTransactionAdapter(PlatformTransactionManager manager) {
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    @Override public <T> T requiresNew(java.util.function.Supplier<T> action) { return transactions.execute(status -> action.get()); }
}
