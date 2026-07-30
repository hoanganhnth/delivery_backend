package com.delivery.observability;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationPropagationTest {

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void propagatesValidatedCorrelationIdAcrossHttp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(CorrelationId.HEADER, "order-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationServletFilter().doFilter(request, response, (req, res) ->
                assertThat(org.slf4j.MDC.get(CorrelationId.MDC_KEY)).isEqualTo("order-123"));

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("order-123");
        assertThat(org.slf4j.MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void addsKafkaCorrelationHeaderWithoutReplacingAnExistingOne() {
        try (CorrelationContext ignored = CorrelationContext.with("order-123")) {
            ProducerRecord<Object, Object> record = new ProducerRecord<>("order.created", "order-123", "{}");
            new CorrelationKafkaProducerInterceptor().onSend(record);

            assertThat(new String(record.headers().lastHeader(CorrelationId.HEADER).value(), StandardCharsets.UTF_8))
                    .isEqualTo("order-123");
        }
    }
}
