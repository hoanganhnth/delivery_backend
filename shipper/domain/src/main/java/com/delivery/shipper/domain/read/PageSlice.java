package com.delivery.shipper.domain.read;

import java.util.List;

public record PageSlice<T>(List<T> items, PageRequest request, long totalItems) {
    public PageSlice {
        if (items == null || totalItems < 0) throw new IllegalArgumentException("page data is invalid");
        if (items.size() > request.size()) throw new IllegalArgumentException("page exceeds requested size");
        items = List.copyOf(items);
    }
    public int totalPages() { return (int) Math.ceil((double) totalItems / request.size()); }
    public boolean hasNext() { return request.page() + 1 < totalPages(); }
}
