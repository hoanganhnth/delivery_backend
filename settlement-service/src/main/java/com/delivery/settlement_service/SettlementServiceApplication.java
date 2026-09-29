package com.delivery.settlement_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.delivery.settlement_service.config.SettlementApplicationConfiguration;

@SpringBootApplication
@Import(SettlementApplicationConfiguration.class)
public class SettlementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SettlementServiceApplication.class, args);
	}

}
