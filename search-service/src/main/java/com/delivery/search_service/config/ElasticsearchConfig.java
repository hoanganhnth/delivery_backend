package com.delivery.search_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.delivery.search_service.repository")
public class ElasticsearchConfig {
    // Relying on default Spring Boot auto-configuration for Elasticsearch
    // using spring.elasticsearch.uris in application.yml
}
