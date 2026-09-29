package com.delivery.shipper.application;

import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.application.api.ShipperResults;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.shipper.domain.read.PageRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Framework-free rating commands and the documented bounded self-read. */
public final class DefaultShipperRatingUseCases implements ShipperUseCases.RateSelf, ShipperUseCases.ReadSelfRatings {
    private final ShipperPorts.ProfileStore profiles;
    private final ShipperPorts.RatingStore ratings;

    public DefaultShipperRatingUseCases(ShipperPorts.ProfileStore profiles, ShipperPorts.RatingStore ratings) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
    }

    @Override public ShipperResults.RatingResult execute(ShipperCommands.SelfRating c) {
        requireActor(c == null ? null : c.actor());
        if (c.shipperId() <= 0 || c.orderId() <= 0) throw new IllegalArgumentException("rating identities must be positive");
        var profile = profiles.findById(c.shipperId())
                .orElseThrow(() -> new IllegalArgumentException("shipper profile not found"));
        if (!profile.identity().owns(c.actor().principalId(), c.actor().legacyUserId()))
            throw new IllegalArgumentException("shipper profile is not owned by actor");
        if (ratings.existsByOrderId(c.orderId())) throw new IllegalArgumentException("order has already been rated");
        if (c.score() < 1 || c.score() > 5) throw new IllegalArgumentException("rating must be between 1 and 5");
        if (c.comment() != null && c.comment().length() > 2000)
            throw new IllegalArgumentException("comment exceeds 2000 characters");
        ShipperResults.RatingResult result = ratings.add(c);
        return new ShipperResults.RatingResult(result.shipperId(),
                BigDecimal.valueOf(result.average()).setScale(1, RoundingMode.HALF_UP).doubleValue(), result.count());
    }

    @Override public List<ShipperResults.RatingItem> execute(ShipperCommands.Actor actor) {
        requireActor(actor);
        long shipperId = profiles.findByPrincipalId(actor.principalId())
                .orElseThrow(() -> new IllegalArgumentException("shipper profile not found")).id();
        return ratings.findByShipperId(shipperId, new PageRequest(0, PageRequest.MAX_SIZE, null, null));
    }

    private static void requireActor(ShipperCommands.Actor actor) {
        if (actor == null || actor.principalId() <= 0 || actor.role() != ShipperRole.SHIPPER)
            throw new IllegalArgumentException("actor is not authorized");
    }
}
