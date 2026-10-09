package com.flashsale.order.observability;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** One trace ID follows a purchase: HTTP request in, service call out, Kafka event out and back in (TDD 68). */
class CorrelationIdTest {
    private static final String TRACE_ID = "6f1c2a9e-0b7d-4c55-9a41-2f0e8d3b7c11";

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void requestTraceIdIsCurrentWhileTheRequestRunsAndClearedAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, TRACE_ID);
        AtomicReference<String> seenByHandler = new AtomicReference<>();

        new CorrelationIdFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seenByHandler.set(CorrelationId.current()));

        assertEquals(TRACE_ID, seenByHandler.get());
        assertNull(CorrelationId.current());
    }

    @Test
    void malformedOrMissingIdsAreReplacedSoClientsCannotInjectLogText() {
        for (String bad : new String[]{null, "", "abc\nFORGED LOG LINE", "x".repeat(65)}) {
            String replacement = CorrelationId.validOrNew(bad);
            assertNotEquals(bad, replacement);
            assertEquals(replacement, CorrelationId.validOrNew(replacement), "a generated ID is itself valid");
        }
        assertEquals(TRACE_ID, CorrelationId.validOrNew(TRACE_ID));
    }

    @Test
    void outgoingServiceCallsCarryTheCurrentTraceId() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(CorrelationId.propagateToHttpCalls()));
        MockRestServiceServer reservationService = MockRestServiceServer.bindTo(restTemplate).build();
        reservationService.expect(method(HttpMethod.GET))
                .andExpect(header(CorrelationId.HEADER, TRACE_ID))
                .andRespond(withSuccess());

        try (MDC.MDCCloseable ignored = CorrelationId.open(TRACE_ID)) {
            restTemplate.getForObject("http://reservation/internal/v1/reservations/res-1", String.class);
        }
        reservationService.verify();
    }

    @Test
    void kafkaEventsCarryTheTraceIdToTheConsumer() {
        ProducerRecord<String, String> published = KafkaTraceHeader.record("payment.requested", "order-1", "{}", TRACE_ID);
        ConsumerRecord<Object, Object> consumed = new ConsumerRecord<>("payment.requested", 0, 0, "order-1", "{}");
        published.headers().forEach(header -> consumed.headers().add(header));
        TraceIdRecordInterceptor interceptor = new TraceIdRecordInterceptor();

        interceptor.intercept(consumed, null);
        assertEquals(TRACE_ID, CorrelationId.current(), "the listener runs under the event's trace ID");
        interceptor.afterRecord(consumed, null);
        assertNull(CorrelationId.current());
    }

    @Test
    void eventsWithoutATraceIdArePublishedWithoutTheHeader() {
        assertNull(KafkaTraceHeader.record("order.created", "order-1", "{}", null).headers().lastHeader(CorrelationId.HEADER));
    }
}
