package com.flashsale.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.kafka")
public class PaymentKafkaProperties {
    private final Topics topics = new Topics();
    private final Retry retry = new Retry();
    public Topics getTopics() { return topics; }
    public Retry getRetry() { return retry; }

    /** Redeliveries of a failing record (after the first attempt) before it goes to its dead-letter topic. */
    public static class Retry {
        private long attempts = 3;
        private long backoffInterval = 1000;
        public long getAttempts() { return attempts; } public void setAttempts(long attempts) { this.attempts = attempts; }
        public long getBackoffInterval() { return backoffInterval; } public void setBackoffInterval(long backoffInterval) { this.backoffInterval = backoffInterval; }
    }

    public static class Topics {
        private String requested;
        private String completed;
        private String failed;
        private String refundRequested;
        public String getRequested() { return requested; } public void setRequested(String requested) { this.requested = requested; }
        public String getCompleted() { return completed; } public void setCompleted(String completed) { this.completed = completed; }
        public String getFailed() { return failed; } public void setFailed(String failed) { this.failed = failed; }
        public String getRefundRequested() { return refundRequested; } public void setRefundRequested(String refundRequested) { this.refundRequested = refundRequested; }
    }
}
