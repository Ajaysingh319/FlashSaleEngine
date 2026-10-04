package com.flashsale.reservation.repository;

import com.flashsale.reservation.document.Inventory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {
    List<Inventory> findByEventIdOrderByTicketTypeIdAsc(String eventId);
}
