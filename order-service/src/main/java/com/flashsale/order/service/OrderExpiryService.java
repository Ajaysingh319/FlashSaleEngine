package com.flashsale.order.service;

import com.flashsale.order.document.Order;
import com.flashsale.order.document.OrderStatus;
import com.flashsale.order.document.PaymentStatus;
import com.flashsale.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Expires orders still awaiting payment after their reservation has expired (PRD 6.6, BR-005: an expired
 * reservation cannot become a paid order). Reservation Service releases the reserved tickets itself when the
 * reservation expires; this only moves the order out of PENDING_PAYMENT so it does not stay pending forever.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryService {
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${order.expiry.check-interval:60000}")
    public int expireUnpaidOrders() {
        Instant now = clock.instant();
        int expired = 0;
        for (Order order : orderRepository.findTop100ByStatusAndReservationExpiresAtBefore(OrderStatus.PENDING_PAYMENT, now)) {
            try {
                if (expire(order.getOrderId(), now)) expired++;
            } catch (RuntimeException exception) {
                log.warn("Could not expire order {}", order.getOrderId(), exception);
            }
        }
        return expired;
    }

    /**
     * Atomic conditional transition: applies only while the order is still awaiting payment and past its
     * reservation expiry, so a concurrently applied payment result or cancellation is never overwritten.
     */
    boolean expire(String orderId, Instant now) {
        Query stillUnpaidAndExpired = new Query(Criteria.where("orderId").is(orderId)
                .and("status").is(OrderStatus.PENDING_PAYMENT.name())
                .and("paymentStatus").is(PaymentStatus.PENDING.name())
                .and("reservationExpiresAt").lt(now));
        Update toExpired = new Update().set("status", OrderStatus.EXPIRED.name()).set("updatedAt", now);
        return mongoTemplate.updateFirst(stillUnpaidAndExpired, toExpired, Order.class).getModifiedCount() == 1;
    }
}
