package com.flashsale.order.observability;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Carries the trace ID over Kafka as the X-Correlation-ID header. Outbox events are published later by a scheduler,
 * so the ID is stored with the outbox event and attached here when it is finally sent.
 */
public final class KafkaTraceHeader {
    private KafkaTraceHeader() {
    }

    public static ProducerRecord<String, String> record(String topic, String key, String payload, String traceId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        if (traceId != null) {
            record.headers().add(CorrelationId.HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    /** The trace ID a consumed event carries, or null for events published without one. */
    public static String read(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
