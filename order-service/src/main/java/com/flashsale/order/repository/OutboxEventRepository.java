package com.flashsale.order.repository;

import com.flashsale.order.document.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for OutboxEvent entity.
 */
@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {
}