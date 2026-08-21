package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.Shipper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperRepository extends JpaRepository<Shipper, Long> {
    Optional<Shipper> findByUserId(Long userId);
    Optional<Shipper> findByPrincipalId(Long principalId);
    Page<Shipper> findByPrincipalIdIsNotNull(Pageable pageable);
    @org.springframework.data.jpa.repository.Query(value = "select s.* from shipper s where s.principal_id is not null "
            + "and not exists (select 1 from shipper_identity_outbox_events e "
            + "where e.event_type = 'shipper.identity.upserted' and e.aggregate_id = s.id) order by s.id", nativeQuery = true)
    List<Shipper> findIdentityOutboxMissing(Pageable pageable);
    List<Shipper> findByIsOnline(Boolean isOnline, Pageable pageable);
    boolean existsByLicenseNumber(String licenseNumber);
    boolean existsByIdCard(String idCard);
}
