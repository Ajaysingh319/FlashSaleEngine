package com.flashsale.reservation.repository;

import com.flashsale.reservation.document.ReservationOutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationOutboxEventRepository extends MongoRepository<ReservationOutboxEvent, String> {
    List<ReservationOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
