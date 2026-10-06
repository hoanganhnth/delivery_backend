package com.delivery.search.application.api;

/** Query text is already admitted/trimmed by HTTP; paging stays with the bound read adapter. */
public record SearchQuery(String text) { }
