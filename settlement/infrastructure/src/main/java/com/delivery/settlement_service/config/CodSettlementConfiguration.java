package com.delivery.settlement_service.config;

import com.delivery.settlement.application.api.ledger.CodSettlementPort;
import com.delivery.settlement.application.api.ledger.CodSettlementUseCase;
import com.delivery.settlement.application.ledger.DefaultCodSettlementUseCase;
import com.delivery.settlement_service.adapter.JpaCodSettlementAdapter;
import com.delivery.settlement_service.repository.SettlementReceiptRepository;
import com.delivery.settlement_service.repository.TransactionRepository;
import com.delivery.settlement_service.service.CodCapacityHoldService;
import com.delivery.settlement_service.service.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Active COD MVP composition is independent of the gated payment/payout provider seam. */
@Configuration
public class CodSettlementConfiguration {
    @Bean
    CodSettlementPort codSettlementPort(TransactionService transactions, TransactionRepository ledger,
            SettlementReceiptRepository receipts, CodCapacityHoldService holds,
            @Value("${spring.datasource.url:}") String dataSourceUrl) {
        return new JpaCodSettlementAdapter(transactions, ledger, receipts, holds, dataSourceUrl);
    }

    @Bean
    CodSettlementUseCase codSettlementUseCase(CodSettlementPort store) {
        return new DefaultCodSettlementUseCase(store);
    }
}
