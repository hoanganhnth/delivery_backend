package com.delivery.shipper.domain.rating;

public record ShipperRating(long shipperId, long customerId, long orderId, int score, String comment) {
    public ShipperRating {
        if (shipperId <= 0 || customerId <= 0 || orderId <= 0) throw new IllegalArgumentException("rating identities must be positive");
        if (score < 1 || score > 5) throw new IllegalArgumentException("rating must be between 1 and 5");
        if (comment != null && comment.length() > 2000) throw new IllegalArgumentException("comment exceeds 2000 characters");
    }
}
