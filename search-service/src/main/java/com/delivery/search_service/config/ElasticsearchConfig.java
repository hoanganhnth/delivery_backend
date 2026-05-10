package com.delivery.search_service.config;
 
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
 
@Configuration
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig {
    // Elasticsearch repositories are auto-configured by Spring Boot 
    // but can be disabled via spring.data.elasticsearch.repositories.enabled in application.yml
}
