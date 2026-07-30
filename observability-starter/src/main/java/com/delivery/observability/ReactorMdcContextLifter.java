package com.delivery.observability;

import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * Restores correlation MDC around every Reactor signal. Reactor Context is the
 * source of truth; this bridge never copies security identity into MDC.
 */
final class ReactorMdcContextLifter<T> implements CoreSubscriber<T> {
    private static final String HOOK_NAME = "delivery-correlation-mdc";
    private final CoreSubscriber<? super T> actual;

    private ReactorMdcContextLifter(CoreSubscriber<? super T> actual) {
        this.actual = actual;
    }

    static void install() {
        Hooks.onEachOperator(HOOK_NAME, Operators.lift((scannable, subscriber) ->
                new ReactorMdcContextLifter<>(subscriber)));
    }

    @Override public Context currentContext() { return actual.currentContext(); }
    @Override public void onSubscribe(Subscription subscription) { withMdc(() -> actual.onSubscribe(subscription)); }
    @Override public void onNext(T value) { withMdc(() -> actual.onNext(value)); }
    @Override public void onError(Throwable error) { withMdc(() -> actual.onError(error)); }
    @Override public void onComplete() { withMdc(actual::onComplete); }

    private void withMdc(Runnable action) {
        String previous = MDC.get(CorrelationId.MDC_KEY);
        String correlationId = currentContext().getOrDefault(CorrelationId.MDC_KEY, null);
        if (CorrelationId.isValid(correlationId)) {
            MDC.put(CorrelationId.MDC_KEY, correlationId);
        } else {
            MDC.remove(CorrelationId.MDC_KEY);
        }
        try {
            action.run();
        } finally {
            if (previous == null) MDC.remove(CorrelationId.MDC_KEY);
            else MDC.put(CorrelationId.MDC_KEY, previous);
        }
    }
}
