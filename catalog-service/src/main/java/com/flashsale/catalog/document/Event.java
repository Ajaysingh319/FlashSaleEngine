package com.flashsale.catalog.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    private String id;
    private String name;
    private String description;
    private String venue;
    private String city;
    private Instant startTime;
    private Instant endTime;
    private Instant saleStartTime;
    private Instant saleEndTime;
    private String status; // DRAFT, UPCOMING, ON_SALE, SOLD_OUT, COMPLETED, CANCELLED
    private Instant createdAt;
    private Instant updatedAt;
}