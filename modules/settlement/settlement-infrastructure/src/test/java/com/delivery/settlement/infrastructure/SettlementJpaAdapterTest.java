package com.delivery.settlement.infrastructure;

import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.repository.BalanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan("com.delivery.settlement_service.entity")
@EnableJpaRepositories("com.delivery.settlement_service.repository")
class SettlementJpaAdapterTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Autowired
    private BalanceRepository balances;

    @Test
    void persistsAndQueriesBalanceByEntityIdentity() {
        balances.save(Balance.builder()
                .entityId(42L)
                .entityType(EntityType.SHIPPER)
                .availableBalance(new BigDecimal("12.50"))
                .build());

        assertThat(balances.findByEntityIdAndEntityType(42L, EntityType.SHIPPER))
                .get()
                .extracting(Balance::getAvailableBalance)
                .isEqualTo(new BigDecimal("12.50"));
    }
}
