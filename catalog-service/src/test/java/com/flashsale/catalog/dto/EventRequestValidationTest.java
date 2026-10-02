package com.flashsale.catalog.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EventRequestValidationTest {

    private static final Instant T0 = Instant.parse("2026-12-01T18:00:00Z");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    static EventRequest validRequest() {
        EventRequest request = new EventRequest();
        request.setName("Delhi Music Festival");
        request.setDescription("Three stages, two days");
        request.setVenue("JLN Stadium");
        request.setCity("Delhi");
        request.setStartTime(T0);
        request.setEndTime(T0.plusSeconds(6 * 3600));
        request.setSaleStartTime(T0.minusSeconds(30 * 24 * 3600));
        request.setSaleEndTime(T0.minusSeconds(3600));
        request.setStatus("UPCOMING");
        return request;
    }

    private static Set<String> violatedProperties(EventRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertTrue(violatedProperties(validRequest()).isEmpty());
    }

    @Test
    void descriptionAndStatusAreOptional() {
        EventRequest request = validRequest();
        request.setDescription(null);
        request.setStatus(null);

        assertTrue(violatedProperties(request).isEmpty());
    }

    @Test
    void endTimeEqualToStartTimeIsRejected() {
        EventRequest request = validRequest();
        request.setEndTime(T0);

        assertEquals(Set.of("endTimeAfterStartTime"), violatedProperties(request));
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() {
        EventRequest request = validRequest();
        request.setEndTime(T0.minusSeconds(1));

        assertEquals(Set.of("endTimeAfterStartTime"), violatedProperties(request));
    }

    @Test
    void saleEndTimeBeforeSaleStartTimeIsRejected() {
        EventRequest request = validRequest();
        request.setSaleEndTime(request.getSaleStartTime().minusSeconds(1));

        assertEquals(Set.of("saleEndTimeNotBeforeSaleStartTime"), violatedProperties(request));
    }

    @Test
    void saleEndTimeEqualToSaleStartTimeIsAllowed() {
        EventRequest request = validRequest();
        request.setSaleEndTime(request.getSaleStartTime());

        assertTrue(violatedProperties(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "UPCOMING", "ON_SALE", "SOLD_OUT", "COMPLETED", "CANCELLED"})
    void everyPrdStatusIsAccepted(String status) {
        EventRequest request = validRequest();
        request.setStatus(status);

        assertTrue(violatedProperties(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "on_sale", "", "ON_SALE "})
    void unknownStatusIsRejected(String status) {
        EventRequest request = validRequest();
        request.setStatus(status);

        assertEquals(Set.of("status"), violatedProperties(request));
    }

    @Test
    void requiredFieldsAreEnforced() {
        EventRequest request = new EventRequest();
        request.setName(" ");

        assertEquals(Set.of("name", "venue", "city", "startTime", "endTime", "saleStartTime", "saleEndTime"),
                violatedProperties(request));
    }
}
