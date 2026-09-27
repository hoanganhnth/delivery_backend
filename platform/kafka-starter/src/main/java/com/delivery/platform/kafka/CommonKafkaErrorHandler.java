package com.delivery.platform.kafka;

import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Shared finite retry policy: 1s, 2s and 4s, then publish to the source
 * topic's DLT while retaining the source partition.
 */
public class CommonKafkaErrorHandler extends DefaultErrorHandler {

    public static final long INITIAL_INTERVAL_MILLIS = 1_000L;
    public static final double MULTIPLIER = 2.0d;
    public static final long MAX_INTERVAL_MILLIS = 4_000L;
    public static final int MAX_RETRIES = 3;
    public static final String DEFAULT_DLT_SUFFIX = ".DLT";

    private final ExponentialBackOff retryBackOff;
    private final DeadLetterPublishingRecoverer deadLetterRecoverer;
    private final String dltSuffix;

    public CommonKafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        this(kafkaTemplate, DEFAULT_DLT_SUFFIX);
    }

    public CommonKafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate, String dltSuffix) {
        this(createRecoverer(kafkaTemplate, dltSuffix), createBackOff(), dltSuffix);
    }

    private CommonKafkaErrorHandler(DeadLetterPublishingRecoverer recoverer,
            ExponentialBackOff retryBackOff, String dltSuffix) {
        super(recoverer, retryBackOff);
        this.deadLetterRecoverer = recoverer;
        this.retryBackOff = retryBackOff;
        this.dltSuffix = requireSuffix(dltSuffix);
        setCommitRecovered(true);
        setAckAfterHandle(true);
    }

    public ExponentialBackOff retryBackOff() {
        return retryBackOff;
    }

    public DeadLetterPublishingRecoverer deadLetterRecoverer() {
        return deadLetterRecoverer;
    }

    public String dltSuffix() {
        return dltSuffix;
    }

    public static ExponentialBackOff createBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(INITIAL_INTERVAL_MILLIS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MILLIS);
        backOff.setMaxAttempts(MAX_RETRIES);
        return backOff;
    }

    public static String deadLetterTopic(String sourceTopic) {
        return deadLetterTopic(sourceTopic, DEFAULT_DLT_SUFFIX);
    }

    public static String deadLetterTopic(String sourceTopic, String dltSuffix) {
        return Objects.requireNonNull(sourceTopic, "sourceTopic")
                + requireSuffix(dltSuffix);
    }

    private static DeadLetterPublishingRecoverer createRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate, String dltSuffix) {
        Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        String suffix = requireSuffix(dltSuffix);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) -> new TopicPartition(
                        deadLetterTopic(record.topic(), suffix), record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    private static String requireSuffix(String suffix) {
        return Objects.requireNonNull(suffix, "dltSuffix").isBlank() ? DEFAULT_DLT_SUFFIX : suffix;
    }
}
