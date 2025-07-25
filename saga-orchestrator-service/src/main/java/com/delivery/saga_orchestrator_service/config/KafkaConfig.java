// // config/KafkaConfig.java
// package com.delivery.saga_orchestrator_service.config;

// import org.apache.kafka.clients.consumer.ConsumerConfig;
// import org.apache.kafka.common.serialization.StringDeserializer;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.kafka.annotation.EnableKafka;
// import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
// import org.springframework.kafka.core.*;

// import java.util.HashMap;
// import java.util.Map;

// @Configuration
// @EnableKafka
// public class KafkaConfig {

//     @Bean
//     public ConsumerFactory<String, Object> consumerFactory() {
//         Map<String, Object> props = new HashMap<>();
//         props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
//         props.put(ConsumerConfig.GROUP_ID_CONFIG, "saga");
//         props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//         props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonDeserializer.class);
//         props.put("spring.json.trusted.packages", "*");

//         return new DefaultKafkaConsumerFactory<>(props);
//     }

//     @Bean
//     public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
//         var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
//         factory.setConsumerFactory(consumerFactory());
//         return factory;
//     }
// }
