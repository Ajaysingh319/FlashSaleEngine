package com.flashsale.reservation.repository;

import com.flashsale.reservation.document.Reservation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {
    List<Reservation> findByUserId(String userId);
    Optional<Reservation> findByIdAndStatus(String id, String status);
}