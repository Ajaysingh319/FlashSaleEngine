package com.flashsale.payment.repository;

import com.flashsale.payment.document.PaymentProcessedEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentProcessedEventRepository extends MongoRepository<PaymentProcessedEvent, String> {
    boolean existsByEventId(String eventId);
}
