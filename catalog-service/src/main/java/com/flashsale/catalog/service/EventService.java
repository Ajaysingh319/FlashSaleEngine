package com.flashsale.catalog.service;

import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.dto.EventRequest;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.dto.EventSearchRequest;
import com.flashsale.catalog.exception.EventNotFoundException;
import com.flashsale.catalog.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    /** Client sort keys mapped to document fields; anything else is rejected. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "city", "city",
            "startTime", "startTime",
            "endTime", "endTime",
            "saleStartTime", "saleStartTime",
            "saleEndTime", "saleEndTime",
            "createdAt", "createdAt");

    private static final List<String> SEARCH_FIELDS = List.of("name", "description", "venue", "city");

    private final EventRepository eventRepository;
    private final MongoTemplate mongoTemplate;

    public EventResponse createEvent(EventRequest request) {
        Event event = new Event();
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setCity(request.getCity());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setSaleEndTime(request.getSaleEndTime());
        event.setStatus(request.getStatus() != null ? request.getStatus() : "DRAFT");
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        Event saved = eventRepository.save(event);
        return mapToResponse(saved);
    }

    public EventResponse getEventById(String id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));
        return mapToResponse(event);
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lists events matching the optional search text and filters, sorted by a whitelisted field
     * (ties broken by id for stable paging) and paginated.
     */
    public Page<EventResponse> searchEvents(EventSearchRequest request) {
        String sortField = SORT_FIELDS.get(request.getSortBy());
        if (sortField == null) {
            throw new IllegalArgumentException("Unsupported sort field: " + request.getSortBy());
        }
        Sort.Direction direction = Sort.Direction.fromString(request.getDirection());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(),
                Sort.by(direction, sortField).and(Sort.by(Sort.Direction.ASC, "_id")));

        List<EventResponse> content = mongoTemplate.find(buildSearchQuery(request).with(pageable), Event.class).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        long total = mongoTemplate.count(buildSearchQuery(request), Event.class);
        return new PageImpl<>(content, pageable, total);
    }

    private Query buildSearchQuery(EventSearchRequest request) {
        List<Criteria> filters = new ArrayList<>();
        if (StringUtils.hasText(request.getQ())) {
            String pattern = escapeRegex(request.getQ().trim());
            filters.add(new Criteria().orOperator(SEARCH_FIELDS.stream()
                    .map(field -> Criteria.where(field).regex(pattern, "i"))
                    .toArray(Criteria[]::new)));
        }
        if (request.getStatus() != null) {
            filters.add(Criteria.where("status").is(request.getStatus().name()));
        }
        if (StringUtils.hasText(request.getCity())) {
            filters.add(Criteria.where("city").regex("^" + escapeRegex(request.getCity().trim()) + "$", "i"));
        }
        if (request.getStartFrom() != null || request.getStartTo() != null) {
            Criteria startTime = Criteria.where("startTime");
            if (request.getStartFrom() != null) {
                startTime = startTime.gte(request.getStartFrom());
            }
            if (request.getStartTo() != null) {
                startTime = startTime.lte(request.getStartTo());
            }
            filters.add(startTime);
        }

        Query query = new Query();
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }
        return query;
    }

    /** Treats user input as literal text inside a MongoDB regular expression. */
    static String escapeRegex(String text) {
        return text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    public List<EventResponse> getEventsOnSale() {
        Instant now = Instant.now();
        return eventRepository.findBySaleStartTimeBeforeAndSaleEndTimeAfter(now, now).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public EventResponse updateEvent(String id, EventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setCity(request.getCity());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setSaleEndTime(request.getSaleEndTime());
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }
        event.setUpdatedAt(Instant.now());

        Event saved = eventRepository.save(event);
        return mapToResponse(saved);
    }

    public void deleteEvent(String id) {
        eventRepository.deleteById(id);
    }

    private EventResponse mapToResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getCity(),
                event.getStartTime(),
                event.getEndTime(),
                event.getSaleStartTime(),
                event.getSaleEndTime(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}