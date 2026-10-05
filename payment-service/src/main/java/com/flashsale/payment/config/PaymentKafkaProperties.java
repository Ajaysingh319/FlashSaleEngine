package com.flashsale.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.kafka")
public class PaymentKafkaProperties {
    private final Topics topics = new Topics();
    public Topics getTopics() { return topics; }

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
