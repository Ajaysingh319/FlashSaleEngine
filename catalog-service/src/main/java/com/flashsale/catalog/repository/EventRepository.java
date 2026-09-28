package com.flashsale.catalog.repository;

import com.flashsale.catalog.document.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {
    List<Event> findByStatus(String status);
    List<Event> findBySaleStartTimeBeforeAndSaleEndTimeAfter(Instant saleStartTimeBefore, Instant saleEndTimeAfter);
    List<Event> findByCity(String city);
}