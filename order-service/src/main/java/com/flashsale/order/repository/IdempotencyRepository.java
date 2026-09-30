package com.flashsale.order.repository;

import com.flashsale.order.document.Idempotency;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Idempotency entity.
 */
@Repository
public interface IdempotencyRepository extends MongoRepository<Idempotency, String> {
}