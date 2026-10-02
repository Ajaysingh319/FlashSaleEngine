package com.flashsale.catalog.service;

import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.document.EventStatus;
import com.flashsale.catalog.dto.EventResponse;
import com.flashsale.catalog.dto.EventSearchRequest;
import com.flashsale.catalog.repository.EventRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventServiceSearchTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final EventService eventService = new EventService(eventRepository, mongoTemplate);

    @BeforeEach
    void setUp() {
        when(mongoTemplate.find(any(Query.class), eq(Event.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(Event.class))).thenReturn(0L);
    }

    private Query capturedFindQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Event.class));
        return captor.getValue();
    }

    private Query capturedCountQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(captor.capture(), eq(Event.class));
        return captor.getValue();
    }

    /** Returns the single filter combined under $and. */
    @SuppressWarnings("unchecked")
    private static List<Document> andFilters(Query query) {
        return (List<Document>) query.getQueryObject().get("$and");
    }

    private static Pattern regexOf(Object value) {
        assertInstanceOf(Pattern.class, value, "expected a regex but got " + value);
        return (Pattern) value;
    }

    @Test
    void defaultsListAllEventsByStartTimeAscendingFirstPageOfTwenty() {
        eventService.searchEvents(new EventSearchRequest());

        Query query = capturedFindQuery();
        assertTrue(query.getQueryObject().isEmpty());
        assertEquals(new Document("startTime", 1).append("_id", 1), query.getSortObject());
        assertEquals(0, query.getSkip());
        assertEquals(20, query.getLimit());
    }

    @Test
    void searchTextMatchesNameDescriptionVenueOrCityCaseInsensitively() {
        EventSearchRequest request = new EventSearchRequest();
        request.setQ("  Music  ");

        eventService.searchEvents(request);

        List<Document> filters = andFilters(capturedFindQuery());
        assertEquals(1, filters.size());
        @SuppressWarnings("unchecked")
        List<Document> alternatives = (List<Document>) filters.get(0).get("$or");
        assertEquals(List.of("name", "description", "venue", "city"),
                alternatives.stream().map(d -> d.keySet().iterator().next()).toList());
        for (Document alternative : alternatives) {
            Pattern pattern = regexOf(alternative.values().iterator().next());
            assertEquals("Music", pattern.pattern());
            assertTrue((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0);
        }
    }

    @Test
    void searchTextIsMatchedLiterallyNotAsRegex() {
        EventSearchRequest request = new EventSearchRequest();
        request.setQ("a.b(c*");

        eventService.searchEvents(request);

        @SuppressWarnings("unchecked")
        List<Document> alternatives = (List<Document>) andFilters(capturedFindQuery()).get(0).get("$or");
        Pattern pattern = regexOf(alternatives.get(0).get("name"));
        assertTrue(pattern.matcher("Fest a.b(c* 2026").find());
        assertFalse(pattern.matcher("axb(cc").find());
    }

    @Test
    void escapeRegexEscapesEveryMetacharacter() {
        String raw = "\\^$.|?*+()[]{}";
        Pattern literal = Pattern.compile(EventService.escapeRegex(raw));

        assertTrue(literal.matcher(raw).matches());
        assertEquals("plain text", EventService.escapeRegex("plain text"));
    }

    @Test
    void statusFilterMatchesStatusName() {
        EventSearchRequest request = new EventSearchRequest();
        request.setStatus(EventStatus.ON_SALE);

        eventService.searchEvents(request);

        assertEquals(List.of(new Document("status", "ON_SALE")), andFilters(capturedFindQuery()));
    }

    @Test
    void cityFilterIsCaseInsensitiveExactMatch() {
        EventSearchRequest request = new EventSearchRequest();
        request.setCity(" new delhi ");

        eventService.searchEvents(request);

        Pattern pattern = regexOf(andFilters(capturedFindQuery()).get(0).get("city"));
        assertTrue(pattern.matcher("New Delhi").matches());
        assertTrue(pattern.matcher("NEW DELHI").matches());
        assertFalse(pattern.matcher("Old New Delhi").find());
        assertFalse(pattern.matcher("New Delhi East").find());
    }

    @Test
    void startTimeRangeFilterIsInclusive() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-30T23:59:59Z");
        EventSearchRequest request = new EventSearchRequest();
        request.setStartFrom(from);
        request.setStartTo(to);

        eventService.searchEvents(request);

        assertEquals(List.of(new Document("startTime", new Document("$gte", from).append("$lte", to))),
                andFilters(capturedFindQuery()));
    }

    @Test
    void openEndedStartTimeRange() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        EventSearchRequest request = new EventSearchRequest();
        request.setStartFrom(from);

        eventService.searchEvents(request);

        assertEquals(List.of(new Document("startTime", new Document("$gte", from))), andFilters(capturedFindQuery()));
    }

    @Test
    void filtersAreCombinedWithAndAndCountUsesSameFilterWithoutPaging() {
        EventSearchRequest request = new EventSearchRequest();
        request.setQ("fest");
        request.setStatus(EventStatus.UPCOMING);
        request.setCity("Mumbai");
        request.setStartFrom(Instant.parse("2026-11-01T00:00:00Z"));
        request.setPage(2);
        request.setSize(5);

        eventService.searchEvents(request);

        Query find = capturedFindQuery();
        Query count = capturedCountQuery();
        assertEquals(4, andFilters(find).size());
        assertEquals(find.getQueryObject().toString(), count.getQueryObject().toString());
        assertEquals(0, count.getSkip());
        assertEquals(0, count.getLimit());
    }

    @Test
    void sortsByRequestedWhitelistedFieldAndDirection() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSortBy("name");
        request.setDirection("DESC");

        eventService.searchEvents(request);

        assertEquals(new Document("name", -1).append("_id", 1), capturedFindQuery().getSortObject());
    }

    @Test
    void rejectsSortFieldOutsideWhitelistEvenIfValidationIsBypassed() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSortBy("password");

        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(request));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejectsUnknownDirectionEvenIfValidationIsBypassed() {
        EventSearchRequest request = new EventSearchRequest();
        request.setDirection("sideways");

        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(request));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void appliesPagingAndReturnsPageMetadata() {
        Event event = new Event();
        event.setId("evt-1");
        event.setName("Delhi Music Festival");
        event.setCity("Delhi");
        event.setStatus("ON_SALE");
        when(mongoTemplate.find(any(Query.class), eq(Event.class))).thenReturn(List.of(event));
        when(mongoTemplate.count(any(Query.class), eq(Event.class))).thenReturn(11L);
        EventSearchRequest request = new EventSearchRequest();
        request.setPage(2);
        request.setSize(5);

        Page<EventResponse> page = eventService.searchEvents(request);

        Query query = capturedFindQuery();
        assertEquals(10, query.getSkip());
        assertEquals(5, query.getLimit());
        assertEquals(11, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(1, page.getContent().size());
        assertEquals("evt-1", page.getContent().get(0).getId());
        assertEquals("ON_SALE", page.getContent().get(0).getStatus());
    }
}
