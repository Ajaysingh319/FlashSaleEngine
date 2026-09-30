package com.flashsale.reservation.repository;

import com.flashsale.reservation.document.Reservation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;
import java.util.Collection;

@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {
    List<Reservation> findByUserId(String userId);
    List<Reservation> findByUserIdAndEventIdAndStatusIn(String userId, String eventId, Collection<String> statuses);
    List<Reservation> findByStatusAndExpiresAtBefore(String status, Instant expiresAt);
}
