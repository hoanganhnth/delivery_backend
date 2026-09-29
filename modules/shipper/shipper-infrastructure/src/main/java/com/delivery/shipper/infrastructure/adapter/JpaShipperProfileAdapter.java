package com.delivery.shipper.infrastructure.adapter;

import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.application.api.ShipperResults;
import com.delivery.shipper.application.api.ShipperSnapshot;
import com.delivery.shipper.domain.identity.IdentityRef;
import com.delivery.shipper.domain.read.PageRequest;
import com.delivery.shipper.domain.read.PageSlice;
import com.delivery.shipper.infrastructure.entity.Shipper;
import com.delivery.shipper.infrastructure.repository.ShipperRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter; JPA types never cross the application port. */
@Component
public class JpaShipperProfileAdapter implements ShipperPorts.ProfileStore, ShipperPorts.IdentityStatusStore {
    private final ShipperRepository repository;

    public JpaShipperProfileAdapter(ShipperRepository repository) { this.repository = repository; }

    @Override @Transactional
    public ShipperSnapshot insert(ShipperCommands.CreateProfile c) {
        Shipper e = new Shipper();
        e.setPrincipalId(c.actor().principalId()); e.setUserId(c.actor().legacyUserId());
        e.setFullName(c.fullName()); e.setVehicleType(c.vehicleType()); e.setLicenseNumber(c.licenseNumber());
        e.setIdCard(c.idCard()); e.setPhone(c.phone()); e.setLicensePlate(c.licensePlate());
        return snapshot(repository.save(e));
    }
    @Override public Optional<ShipperSnapshot> findByPrincipalId(long id) { return repository.findByPrincipalId(id).map(this::snapshot); }
    @Override public Optional<ShipperSnapshot> findById(long id) { return repository.findById(id).map(this::snapshot); }
    @Override @Transactional public ShipperSnapshot update(ShipperCommands.UpdateProfile c) {
        Shipper e = repository.findById(c.shipperId()).orElseThrow();
        if (c.fullName() != null) e.setFullName(c.fullName()); if (c.vehicleType() != null) e.setVehicleType(c.vehicleType());
        if (c.licenseNumber() != null) e.setLicenseNumber(c.licenseNumber()); if (c.idCard() != null) e.setIdCard(c.idCard());
        if (c.phone() != null) e.setPhone(c.phone()); if (c.licensePlate() != null) e.setLicensePlate(c.licensePlate());
        return snapshot(repository.save(e));
    }
    @Override @Transactional public ShipperSnapshot updateOnline(long id, boolean online) {
        Shipper e = repository.findById(id).orElseThrow(); e.setIsOnline(online); return snapshot(repository.save(e));
    }
    @Override public ShipperResults.SelfPage page(PageRequest request) {
        org.springframework.data.domain.Page<Shipper> page;
        var pageable = org.springframework.data.domain.PageRequest.of(request.page(), request.size());
        if (request.isOnline() != null) {
            page = repository.findByIsOnline(request.isOnline(), pageable);
        } else {
            page = repository.findByPrincipalIdIsNotNull(pageable);
        }
        return new ShipperResults.SelfPage(new PageSlice<>(page.getContent().stream().map(this::snapshot).toList(), request, page.getTotalElements()));
    }
    @Override public boolean existsByLicenseNumber(String value, Long excluded) { return repository.existsByLicenseNumber(value); }
    @Override public boolean existsByIdCard(String value, Long excluded) { return repository.existsByIdCard(value); }
    @Override @Transactional public ShipperResults.IdentityStatusResult apply(ShipperCommands.IdentityStatusProjection c) {
        Shipper e = repository.findByPrincipalId(c.principalId()).orElse(null);
        if (e == null) return new ShipperResults.IdentityStatusResult(c.principalId(), c.status(), c.version(), false);
        long current = e.getIdentityStatusVersion() == null ? 0 : e.getIdentityStatusVersion();
        if (c.version() > current) { e.setIdentityStatus(c.status()); e.setIdentityStatusVersion(c.version()); repository.save(e); return new ShipperResults.IdentityStatusResult(c.principalId(), c.status(), c.version(), true); }
        return new ShipperResults.IdentityStatusResult(c.principalId(), c.status(), c.version(), false);
    }
    private ShipperSnapshot snapshot(Shipper e) {
        return new ShipperSnapshot(e.getId(), new IdentityRef(e.getPrincipalId(), e.getUserId()), e.getFullName(), e.getVehicleType(),
                e.getLicenseNumber(), e.getIdCard(), e.getPhone(), e.getLicensePlate(), Boolean.TRUE.equals(e.getIsOnline()),
                e.getCompletedDeliveries() == null ? 0 : e.getCompletedDeliveries(), e.getRating() == null ? 0 : e.getRating().doubleValue(), 0,
                e.getIdentityStatus(), e.getIdentityStatusVersion() == null ? 0 : e.getIdentityStatusVersion());
    }
}
