package com.delivery.search.application.api;

/** Result is opaque to orchestration, preserving the adapter's page instance and metadata. */
public interface SearchQueryPort<R> {
    Reader<R> resolve();
    interface Reader<R> {
        R find(SearchQuery query);
    }
}
