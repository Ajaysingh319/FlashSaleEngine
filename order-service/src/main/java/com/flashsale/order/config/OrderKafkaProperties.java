package com.flashsale.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.kafka")
public class OrderKafkaProperties {
    private long outboxPublishInterval = 5000;
    private final Topics topics = new Topics();

    public long getOutboxPublishInterval() { return outboxPublishInterval; }
    public void setOutboxPublishInterval(long outboxPublishInterval) { this.outboxPublishInterval = outboxPublishInterval; }
    public Topics getTopics() { return topics; }

    public static class Topics {
        private String created;
        private String cancelled;
        private String confirmed;
        private String paymentFailed;
        private String paymentRequested;
        private String paymentRefundRequested;
        public String getCreated() { return created; } public void setCreated(String created) { this.created = created; }
        public String getCancelled() { return cancelled; } public void setCancelled(String cancelled) { this.cancelled = cancelled; }
        public String getConfirmed() { return confirmed; } public void setConfirmed(String confirmed) { this.confirmed = confirmed; }
        public String getPaymentFailed() { return paymentFailed; } public void setPaymentFailed(String paymentFailed) { this.paymentFailed = paymentFailed; }
        public String getPaymentRequested() { return paymentRequested; } public void setPaymentRequested(String paymentRequested) { this.paymentRequested = paymentRequested; }
        public String getPaymentRefundRequested() { return paymentRefundRequested; } public void setPaymentRefundRequested(String paymentRefundRequested) { this.paymentRefundRequested = paymentRefundRequested; }
    }
}
