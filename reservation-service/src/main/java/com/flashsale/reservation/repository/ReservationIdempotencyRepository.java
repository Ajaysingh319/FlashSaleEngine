package com.flashsale.reservation.repository;

import com.flashsale.reservation.document.ReservationIdempotency;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationIdempotencyRepository extends MongoRepository<ReservationIdempotency, String> {
    Optional<ReservationIdempotency> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
