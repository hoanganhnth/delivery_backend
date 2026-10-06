package com.delivery.search.application.api;

public interface SearchQueryUseCase<R> {
    R search(SearchQuery query);
}
