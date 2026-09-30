package com.flashsale.order.repository;

import com.flashsale.order.document.ProcessedEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for ProcessedEvent entity.
 */
@Repository
public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
    boolean existsByEventId(String eventId);
}
