package com.delivery.shipper.application.api;

import com.delivery.shipper.domain.read.PageSlice;

public interface ShipperResults {
    public record CreateProfileResult(ShipperSnapshot profile) { }
    public record RatingResult(long shipperId, double average, long count) { }
    public record RatingItem(long shipperId, long customerId, long orderId, int score, String comment) { }
    public record IdentityStatusResult(long principalId, String status, long version, boolean applied) { }
    public record IdentityUpsertedOutbox(long shipperId, long principalId, Long legacyUserId, long mappingVersion) { }
    public record SelfPage(PageSlice<ShipperSnapshot> page) { }
}
