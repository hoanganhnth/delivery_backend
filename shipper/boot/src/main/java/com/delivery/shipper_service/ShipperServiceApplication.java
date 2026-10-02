package com.delivery.shipper_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.delivery.shipper_service", "com.delivery.shipper"})

@EnableScheduling
public class ShipperServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShipperServiceApplication.class, args);
	}

}
