package com.flashsale.order.repository;

import com.flashsale.order.document.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Order entity.
 */
@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
}