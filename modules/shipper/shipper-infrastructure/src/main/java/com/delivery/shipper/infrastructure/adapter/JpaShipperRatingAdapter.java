package com.delivery.shipper.infrastructure.adapter;

import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.application.api.ShipperResults;
import com.delivery.shipper.domain.read.PageRequest;
import com.delivery.shipper.infrastructure.entity.ShipperRating;
import com.delivery.shipper.infrastructure.repository.ShipperRatingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public final class JpaShipperRatingAdapter implements ShipperPorts.RatingStore {
    private final ShipperRatingRepository repository;
    public JpaShipperRatingAdapter(ShipperRatingRepository repository) { this.repository = repository; }
    @Override @Transactional public ShipperResults.RatingResult add(ShipperCommands.SelfRating c) {
        ShipperRating e = new ShipperRating(); e.setShipperId(c.shipperId()); e.setCustomerId(c.actor().legacyUserId());
        e.setOrderId(c.orderId()); e.setRating(c.score()); e.setComment(c.comment()); repository.save(e);
        Double average = repository.findAverageRatingByShipperId(c.shipperId());
        return new ShipperResults.RatingResult(c.shipperId(), BigDecimal.valueOf(average == null ? 0 : average).setScale(1, RoundingMode.HALF_UP).doubleValue(), repository.countByShipperId(c.shipperId()));
    }
    @Override public java.util.List<ShipperResults.RatingItem> findByShipperId(long id, PageRequest request) {
        return repository.findByShipperIdOrderByCreatedAtDesc(id, org.springframework.data.domain.PageRequest.of(request.page(), request.size()))
                .stream().map(e -> new ShipperResults.RatingItem(e.getShipperId(), e.getCustomerId(), e.getOrderId(), e.getRating(), e.getComment())).toList();
    }
    @Override public boolean existsByOrderId(long orderId) { return repository.existsByOrderId(orderId); }
}
