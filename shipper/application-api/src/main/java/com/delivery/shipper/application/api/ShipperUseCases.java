package com.delivery.shipper.application.api;

import com.delivery.shipper.domain.read.PageRequest;

public interface ShipperUseCases {
    public interface CreateProfile { ShipperResults.CreateProfileResult execute(ShipperCommands.CreateProfile command); }
    public interface UpdateProfile { ShipperSnapshot execute(ShipperCommands.UpdateProfile command); }
    public interface ReadSelf { ShipperSnapshot execute(ShipperCommands.Actor actor); }
    public interface ReadById { ShipperSnapshot execute(ShipperCommands.Actor actor, long shipperId); }
    public interface ReadPage { ShipperResults.SelfPage execute(ShipperCommands.Actor actor, PageRequest request); }
    public interface SetOnlineStatus { ShipperSnapshot execute(ShipperCommands.SetOnlineStatus command); }
    public interface RateSelf { ShipperResults.RatingResult execute(ShipperCommands.SelfRating command); }
    public interface ReadSelfRatings { java.util.List<ShipperResults.RatingItem> execute(ShipperCommands.Actor actor); }
    public interface TrackingOffline { void execute(ShipperCommands.TrackingOffline command); }
    public interface ProjectIdentityStatus { ShipperResults.IdentityStatusResult execute(ShipperCommands.IdentityStatusProjection command); }
}
