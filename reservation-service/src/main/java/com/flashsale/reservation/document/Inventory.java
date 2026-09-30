package com.flashsale.reservation.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "inventory")
@CompoundIndex(name = "event_ticket_type_unique", def = "{'eventId': 1, 'ticketTypeId': 1}", unique = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    private String id; // ticketTypeId
    private String ticketTypeId;
    private String eventId;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;
    private Instant updatedAt;
}
