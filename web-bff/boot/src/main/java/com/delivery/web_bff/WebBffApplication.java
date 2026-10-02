package com.delivery.web_bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.delivery.web_bff.infrastructure.session")
@EnableJpaRepositories("com.delivery.web_bff.infrastructure.session")
public class WebBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebBffApplication.class, args);
    }
}
