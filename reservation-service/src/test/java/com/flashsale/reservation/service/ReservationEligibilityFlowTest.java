package com.flashsale.reservation.service;

import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationIdempotency;
import com.flashsale.reservation.dto.CatalogEventResponse;
import com.flashsale.reservation.dto.CatalogTicketTypeResponse;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.exception.CatalogUnavailableException;
import com.flashsale.reservation.exception.EventCancelledException;
import com.flashsale.reservation.exception.EventNotFoundException;
import com.flashsale.reservation.exception.EventNotOnSaleException;
import com.flashsale.reservation.exception.IdempotencyConflictException;
import com.flashsale.reservation.exception.TicketTypeNotFoundException;
import com.flashsale.reservation.outbox.ReservationOutboxService;
import com.flashsale.reservation.repository.InventoryRepository;
import com.flashsale.reservation.repository.ReservationIdempotencyRepository;
import com.flashsale.reservation.repository.ReservationRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReservationService wired with the real EventSaleEligibilityValidator, a mocked Catalog client and a
 * fixed clock. Proves that ineligible events never reach locking or inventory mutation.
 */
@SuppressWarnings("unchecked")
class ReservationEligibilityFlowTest {

    private static final Instant SALE_START = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant SALE_END = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant DURING_SALE = Instant.parse("2026-11-01T11:00:00Z");

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final ReservationIdempotencyRepository idempotencyRepository = mock(ReservationIdempotencyRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final ReservationOutboxService outboxService = mock(ReservationOutboxService.class);
    private final CatalogServiceClient catalogClient = mock(CatalogServiceClient.class);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(reservationRepository.findByUserIdAndEventIdAndStatusIn(anyString(), anyString(), anyCollection())).thenReturn(List.of());
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId("res-1");
            return reservation;
        });
        when(catalogClient.findTicketType("ticket-1"))
                .thenReturn(Optional.of(new CatalogTicketTypeResponse("ticket-1", "event-1", 4999.0)));
    }

    private ReservationService serviceAt(Instant now) {
        EventSaleEligibilityValidator validator =
                new EventSaleEligibilityValidator(catalogClient, Clock.fixed(now, ZoneOffset.UTC));
        return new ReservationService(reservationRepository, inventoryRepository, idempotencyRepository,
                mongoTemplate, redisTemplate, transactionTemplate, outboxService, validator, catalogClient);
    }

    private void catalogReturns(String status) {
        when(catalogClient.findEvent("event-1"))
                .thenReturn(Optional.of(new CatalogEventResponse("event-1", status, SALE_START, SALE_END)));
    }

    private static ReservationRequest request(int quantity) {
        ReservationRequest request = new ReservationRequest();
        request.setEventId("event-1");
        request.setTicketTypeId("ticket-1");
        request.setQuantity(quantity);
        return request;
    }

    /** Nothing was locked, reserved, persisted or published. */
    private void assertNoSideEffects() {
        verifyNoInteractions(mongoTemplate, valueOperations, transactionTemplate, outboxService, inventoryRepository);
        verify(reservationRepository, never()).save(any());
        verify(idempotencyRepository, never()).insert(any(ReservationIdempotency.class));
    }

    @Test
    void openSaleProceedsThroughExistingReservationFlow() {
        catalogReturns("ON_SALE");

        ReservationResponse response = serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(2));

        assertEquals("res-1", response.getReservationId());
        assertEquals("ACTIVE", response.getStatus());
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Inventory.class));
        verify(idempotencyRepository).insert(any(ReservationIdempotency.class));
        verify(outboxService).append(eq("reservation.created"), eq("RESERVATION_CREATED"), any());
    }

    // --- Price snapshot (PRD 6.5, TDD 16) ---

    @Test
    void reservationStoresAndReturnsUnitPriceAndAmount() {
        catalogReturns("ON_SALE");

        ReservationResponse response = serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(2));

        assertEquals(0, new BigDecimal("4999.0").compareTo(response.getUnitPrice()));
        assertEquals(0, new BigDecimal("9998.0").compareTo(response.getAmount()));
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(saved.capture());
        assertEquals(0, new BigDecimal("4999.0").compareTo(saved.getValue().getUnitPrice()));
        assertEquals(0, new BigDecimal("9998.0").compareTo(saved.getValue().getAmount()));
    }

    @Test
    void unknownTicketTypeIsRejectedWithoutTouchingInventory() {
        catalogReturns("ON_SALE");
        when(catalogClient.findTicketType("ticket-1")).thenReturn(Optional.empty());

        assertThrows(TicketTypeNotFoundException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(1)));
        assertNoSideEffects();
    }

    @Test
    void ticketTypeOfAnotherEventIsRejectedWithoutTouchingInventory() {
        catalogReturns("ON_SALE");
        when(catalogClient.findTicketType("ticket-1"))
                .thenReturn(Optional.of(new CatalogTicketTypeResponse("ticket-1", "other-event", 4999.0)));

        assertThrows(TicketTypeNotFoundException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(1)));
        assertNoSideEffects();
    }

    @Test
    void priceLookupFailureFailsClosedWithoutTouchingInventory() {
        catalogReturns("ON_SALE");
        when(catalogClient.findTicketType("ticket-1")).thenThrow(new CatalogUnavailableException("down"));

        assertThrows(CatalogUnavailableException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(1)));
        assertNoSideEffects();
    }

    @Test
    void replayReturnsStoredAmountWithoutRepricing() {
        ReservationIdempotency record = new ReservationIdempotency();
        record.setUserId("user-1");
        record.setIdempotencyKey("key-1");
        record.setReservationId("res-1");
        record.setRequestFingerprint(fingerprint(request(2)));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(record));
        Reservation original = new Reservation();
        original.setId("res-1");
        original.setStatus("ACTIVE");
        original.setUnitPrice(new BigDecimal("4999.0"));
        original.setAmount(new BigDecimal("9998.0"));
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(original));

        ReservationResponse replay = serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(2));

        assertEquals(0, new BigDecimal("9998.0").compareTo(replay.getAmount()));
        verifyNoInteractions(catalogClient);
    }

    @Test
    void reservationAtExactlySaleStartIsAccepted() {
        catalogReturns("ON_SALE");

        assertEquals("ACTIVE", serviceAt(SALE_START).createReservation("user-1", "key-1", request(1)).getStatus());
    }

    @Test
    void reservationAtExactlySaleEndIsAccepted() {
        catalogReturns("ON_SALE");

        assertEquals("ACTIVE", serviceAt(SALE_END).createReservation("user-1", "key-1", request(1)).getStatus());
    }

    @Test
    void saleNotStartedIsRejectedWithoutTouchingInventory() {
        catalogReturns("UPCOMING");

        EventNotOnSaleException exception = assertThrows(EventNotOnSaleException.class,
                () -> serviceAt(SALE_START.minusMillis(1)).createReservation("user-1", "key-1", request(1)));

        assertTrue(exception.getMessage().contains("not started"));
        assertNoSideEffects();
    }

    @Test
    void saleEndedIsRejectedWithoutTouchingInventory() {
        catalogReturns("ON_SALE");

        EventNotOnSaleException exception = assertThrows(EventNotOnSaleException.class,
                () -> serviceAt(SALE_END.plusMillis(1)).createReservation("user-1", "key-1", request(1)));

        assertTrue(exception.getMessage().contains("ended"));
        assertNoSideEffects();
    }

    @Test
    void cancelledEventIsRejectedEvenInsideSaleWindow() {
        catalogReturns("CANCELLED");

        assertThrows(EventCancelledException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(1)));
        assertNoSideEffects();
    }

    @Test
    void unknownEventIsRejectedWithoutTouchingInventory() {
        when(catalogClient.findEvent("event-1")).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(1)));
        assertNoSideEffects();
    }

    @Test
    void catalogUnavailableFailsClosedWithoutTouchingInventory() {
        when(catalogClient.findEvent("event-1")).thenThrow(new CatalogUnavailableException("down"));

        assertThrows(CatalogUnavailableException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(1)));
        assertNoSideEffects();
    }

    @Test
    void idempotentReplaySucceedsWithoutRecheckingCatalogEvenAfterSaleEnds() {
        ReservationIdempotency record = new ReservationIdempotency();
        record.setUserId("user-1");
        record.setIdempotencyKey("key-1");
        record.setReservationId("res-1");
        record.setRequestFingerprint(fingerprint(request(1)));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(record));
        Reservation original = new Reservation();
        original.setId("res-1");
        original.setStatus("ACTIVE");
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(original));

        ReservationResponse replay = serviceAt(SALE_END.plusSeconds(3600)).createReservation("user-1", "key-1", request(1));

        assertEquals("res-1", replay.getReservationId());
        verifyNoInteractions(catalogClient);
        assertNoSideEffects();
    }

    @Test
    void reusedKeyWithDifferentRequestIsStillRejectedAsConflict() {
        ReservationIdempotency record = new ReservationIdempotency();
        record.setUserId("user-1");
        record.setIdempotencyKey("key-1");
        record.setReservationId("res-1");
        record.setRequestFingerprint(fingerprint(request(1)));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class,
                () -> serviceAt(DURING_SALE).createReservation("user-1", "key-1", request(2)));
        verifyNoInteractions(catalogClient);
        assertNoSideEffects();
    }

    private static String fingerprint(ReservationRequest request) {
        try {
            String canonical = request.getEventId() + "\n" + request.getTicketTypeId() + "\n" + request.getQuantity();
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
