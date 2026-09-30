package com.flashsale.payment.repository;

import com.flashsale.payment.document.PaymentOutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentOutboxEventRepository extends MongoRepository<PaymentOutboxEvent, String> {
    List<PaymentOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
