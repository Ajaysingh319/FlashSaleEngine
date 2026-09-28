package com.flashsale.catalog.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ticket_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketType {

    @Id
    private String id;
    private String name;
    private Double price;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;

    @DBRef
    private Event event;

    private Instant createdAt;
    private Instant updatedAt;
}