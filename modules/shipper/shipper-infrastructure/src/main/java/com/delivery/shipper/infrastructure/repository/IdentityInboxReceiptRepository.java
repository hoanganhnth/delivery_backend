package com.delivery.shipper.infrastructure.repository;

import com.delivery.shipper.infrastructure.entity.IdentityInboxReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityInboxReceiptRepository extends JpaRepository<IdentityInboxReceipt, UUID> { }
