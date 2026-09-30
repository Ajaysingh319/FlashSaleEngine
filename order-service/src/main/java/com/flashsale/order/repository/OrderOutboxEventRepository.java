package com.flashsale.order.repository;

import com.flashsale.order.document.OrderOutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderOutboxEventRepository extends MongoRepository<OrderOutboxEvent, String> {
    List<OrderOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
