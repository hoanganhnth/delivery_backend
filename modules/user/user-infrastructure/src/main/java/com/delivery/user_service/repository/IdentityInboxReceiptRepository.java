package com.delivery.user_service.repository;

import com.delivery.user_service.entity.IdentityInboxReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityInboxReceiptRepository extends JpaRepository<IdentityInboxReceipt, UUID> { }
