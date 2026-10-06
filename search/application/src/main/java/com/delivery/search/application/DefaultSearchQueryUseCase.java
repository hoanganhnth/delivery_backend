package com.delivery.search.application;

import com.delivery.search.application.api.SearchQuery;
import com.delivery.search.application.api.SearchQueryPort;
import com.delivery.search.application.api.SearchQueryUseCase;
import com.delivery.search.application.api.SearchUnavailableException;
import java.util.function.Consumer;

/** Query availability policy without depending on repository or page implementations. */
public final class DefaultSearchQueryUseCase<R> implements SearchQueryUseCase<R> {
    private final SearchQueryPort<R> port;
    private final String resource;
    private final Consumer<RuntimeException> failureObserver;

    public DefaultSearchQueryUseCase(SearchQueryPort<R> port, String resource,
                                    Consumer<RuntimeException> failureObserver) {
        this.port = port;
        this.resource = resource;
        this.failureObserver = failureObserver;
    }

    @Override
    public R search(SearchQuery query) {
        // Provider resolution was outside the host's query catch block and remains so.
        SearchQueryPort.Reader<R> reader = port.resolve();
        if (reader == null) {
            throw new SearchUnavailableException(resource + " search repository is unavailable");
        }
        try {
            return reader.find(query);
        } catch (RuntimeException failure) {
            failureObserver.accept(failure);
            throw new SearchUnavailableException(resource + " search failed", failure);
        }
    }
}
