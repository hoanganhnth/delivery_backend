package com.delivery.observability;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

@AutoConfiguration
public class ObservabilityAutoConfiguration {
    @Bean
    static BeanPostProcessor scheduledObservationSuppressor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ObservationRegistry registry) {
                    registry.observationConfig().observationPredicate(
                            (name, context) -> !(context instanceof ScheduledTaskObservationContext));
                }
                return bean;
            }
        };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    FilterRegistrationBean<CorrelationServletFilter> correlationServletFilter() {
        FilterRegistrationBean<CorrelationServletFilter> registration = new FilterRegistrationBean<>(new CorrelationServletFilter());
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    CorrelationWebFilter correlationWebFilter() { return new CorrelationWebFilter(); }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    Object correlationReactorMdcBridge() {
        ReactorMdcContextLifter.install();
        return new Object();
    }

    @Bean
    @ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
    BeanPostProcessor correlationKafkaContextConfigurer(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof DefaultKafkaProducerFactory<?, ?> producerFactory) {
                    producerFactory.updateConfigs(java.util.Map.of(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                            CorrelationKafkaProducerInterceptor.class.getName()));
                }
                if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> listenerFactory) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    ConcurrentKafkaListenerContainerFactory rawFactory = listenerFactory;
                    rawFactory.setRecordInterceptor(new CorrelationKafkaRecordInterceptor());
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!environment.getProperty("app.observability.kafka-observation-enabled", Boolean.class, true)) {
                    return bean;
                }
                String excludedBeans = environment.getProperty(
                        "app.observability.kafka-observation-excluded-beans", "");
                if (java.util.Arrays.stream(excludedBeans.split(","))
                        .map(String::trim)
                        .anyMatch(beanName::equals)) {
                    return bean;
                }
                if (bean instanceof KafkaTemplate<?, ?> kafkaTemplate) {
                    kafkaTemplate.setObservationEnabled(true);
                }
                if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> listenerFactory) {
                    listenerFactory.getContainerProperties().setObservationEnabled(true);
                }
                return bean;
            }
        };
    }

    @Bean
    RestTemplateCustomizer correlationRestTemplateCustomizer() {
        return restTemplate -> restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(CorrelationId.HEADER, CorrelationContext.currentOrCreate());
            return execution.execute(request, body);
        });
    }

    @Bean
    WebClientCustomizer correlationWebClientCustomizer() {
        return builder -> builder.filter((request, next) -> reactor.core.publisher.Mono.deferContextual(context -> {
            String correlationId = context.getOrDefault(CorrelationId.MDC_KEY, CorrelationContext.currentOrCreate());
            ClientRequest propagated = ClientRequest.from(request)
                    .headers(headers -> headers.set(CorrelationId.HEADER, correlationId))
                    .build();
            return next.exchange(propagated);
        }));
    }
}
