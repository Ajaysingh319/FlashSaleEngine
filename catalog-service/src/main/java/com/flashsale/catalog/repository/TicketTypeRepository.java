package com.flashsale.catalog.repository;

import com.flashsale.catalog.document.InventoryStatus;
import com.flashsale.catalog.document.TicketType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketTypeRepository extends MongoRepository<TicketType, String> {
    List<TicketType> findByEventId(String eventId);
    List<TicketType> findByEventIdAndInventoryStatus(String eventId, InventoryStatus inventoryStatus);
    Optional<TicketType> findByEventIdAndName(String eventId, String name);
}
