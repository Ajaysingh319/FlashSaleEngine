package com.flashsale.catalog.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A ticket category of an event. Catalog owns name, price and the configured capacity (totalQuantity);
 * live available/reserved/sold counts are owned by Reservation Service (PRD 6.4, TDD 5).
 */
@Document(collection = "ticket_types")
// Partial: ticket types saved before eventId existed would otherwise all index as eventId=null and collide.
@CompoundIndex(name = "event_name_unique", def = "{'eventId': 1, 'name': 1}", unique = true,
        partialFilter = "{ 'eventId': { '$exists': true } }")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketType {

    @Id
    private String id;
    private String name;
    private Double price;
    private Integer totalQuantity;
    private InventoryStatus inventoryStatus;

    /** Denormalized from {@link #event} so (eventId, name) can be uniquely indexed. */
    private String eventId;

    @DBRef
    private Event event;

    private Instant createdAt;
    private Instant updatedAt;
}
