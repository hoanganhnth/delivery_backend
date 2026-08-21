package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.IdentityInboxReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityInboxReceiptRepository extends JpaRepository<IdentityInboxReceipt, UUID> { }
