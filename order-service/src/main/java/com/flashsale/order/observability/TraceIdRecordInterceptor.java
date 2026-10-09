package com.flashsale.order.observability;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

/**
 * Restores an event's trace ID while a @KafkaListener handles it, so its logs and the outbox events it writes keep
 * the purchase's trace ID. Spring Boot applies this interceptor to every listener container.
 */
@Component
public class TraceIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.put(CorrelationId.MDC_KEY, CorrelationId.validOrNew(KafkaTraceHeader.read(record)));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationId.MDC_KEY);
    }
}
