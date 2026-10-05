package com.flashsale.order.repository;

import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order entity.
 */
@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByOrderId(String orderId);
    Optional<Order> findByReservationId(String reservationId);
    boolean existsByReservationId(String reservationId);
    List<Order> findByUserId(String userId);
    List<Order> findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus status, Instant now);
}
