package com.flashsale.reservation.service;

import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationStatus;
import com.flashsale.reservation.document.ReservationIdempotency;
import com.flashsale.reservation.dto.CatalogTicketTypeResponse;
import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.exception.IdempotencyConflictException;
import com.flashsale.reservation.exception.InventoryConflictException;
import com.flashsale.reservation.exception.InventoryNotFoundException;
import com.flashsale.reservation.exception.InventoryUnavailableException;
import com.flashsale.reservation.exception.PurchaseLimitExceededException;
import com.flashsale.reservation.outbox.ReservationOutboxService;
import com.flashsale.reservation.repository.InventoryRepository;
import com.flashsale.reservation.repository.ReservationRepository;
import com.flashsale.reservation.repository.ReservationIdempotencyRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    @Mock private ReservationRepository reservationRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ReservationIdempotencyRepository idempotencyRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ReservationOutboxService reservationOutboxService;
    @Mock private EventSaleEligibilityValidator eventSaleEligibilityValidator;
    @Mock private CatalogServiceClient catalogServiceClient;
    @InjectMocks private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(reservationRepository.findByUserIdAndEventIdAndStatusIn(anyString(), anyString(), anyCollection())).thenReturn(List.of());
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(catalogServiceClient.findTicketType(anyString())).thenAnswer(invocation ->
                Optional.of(new CatalogTicketTypeResponse(invocation.getArgument(0), "event-1", 4999.0)));
    }

    @Test
    void createsActiveReservationAndAtomicallyMovesInventoryToReserved() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId("res-1");
            return reservation;
        });

        ReservationResponse response = reservationService.createReservation("user-1", "key-1", request(2));

        assertEquals("ACTIVE", response.getStatus());
        assertEquals("res-1", response.getReservationId());
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Inventory.class));
        assertEquals(-2, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).getInteger("availableQuantity"));
        assertEquals(2, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).getInteger("reservedQuantity"));
    }

    @Test
    void rejectsReservationWhenAtomicInventoryPredicateDoesNotMatch() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(notUpdated());

        assertThrows(InventoryUnavailableException.class,
                () -> reservationService.createReservation("user-1", "key-1", request(2)));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void concurrentAttemptsCannotOversellWhenAtomicUpdateAllowsOnlyOne() throws Exception {
        AtomicInteger available = new AtomicInteger(1);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenAnswer(invocation ->
                available.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0 ? updated() : notUpdated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<ReservationAttempt>> attempts = java.util.stream.IntStream.range(0, 20)
                    .<Callable<ReservationAttempt>>mapToObj(i -> () -> {
                        try {
                            reservationService.createReservation("user-" + i, "key-" + i, request(1));
                            return ReservationAttempt.RESERVED;
                        } catch (InventoryUnavailableException expected) {
                            return ReservationAttempt.INVENTORY_UNAVAILABLE;
                        }
                    }).toList();
            List<Future<ReservationAttempt>> results = pool.invokeAll(attempts);
            List<ReservationAttempt> outcomes = results.stream().map(result -> {
                try { return result.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();

            assertEquals(1, outcomes.stream().filter(outcome -> outcome == ReservationAttempt.RESERVED).count());
            assertEquals(19, outcomes.stream().filter(outcome -> outcome == ReservationAttempt.INVENTORY_UNAVAILABLE).count());
            assertEquals(0, available.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationReleasesReservedInventoryAndSetsCancelled() {
        Reservation active = activeReservation("res-1");
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(active));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class))).thenReturn(active);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());

        reservationService.cancelReservation("res-1", "user-1");

        assertInventoryIncrement("availableQuantity", 1);
        assertInventoryIncrement("reservedQuantity", -1);
        assertTransitionStatus("CANCELLED");
    }

    @Test
    void confirmationMovesReservedInventoryToSold() {
        Reservation active = activeReservation("res-1");
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(active));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class))).thenReturn(active);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());

        reservationService.confirmReservation("res-1");

        assertInventoryIncrement("reservedQuantity", -1);
        assertInventoryIncrement("soldQuantity", 1);
        assertTransitionStatus("CONFIRMED");
    }

    @Test
    void schedulerExpiresActiveReservationsInsteadOfCancellingThem() {
        Reservation active = activeReservation("res-1");
        active.setExpiresAt(Instant.now().minusSeconds(1));
        when(reservationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any())).thenReturn(List.of(active));
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(active));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class))).thenReturn(active);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());

        reservationService.expireReservations();

        assertTransitionStatus("EXPIRED");
        assertInventoryIncrement("availableQuantity", 1);
    }

    @Test
    void enforcesFourTicketPurchaseLimitAcrossActiveAndConfirmedReservations() {
        Reservation existing = activeReservation("res-existing");
        existing.setQuantity(4);
        when(reservationRepository.findByUserIdAndEventIdAndStatusIn(anyString(), anyString(), anyCollection())).thenReturn(List.of(existing));

        assertThrows(PurchaseLimitExceededException.class,
                () -> reservationService.createReservation("user-1", "key-1", request(1)));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void acquiresUserLockBeforeInventoryLockAndUsesTokenCheckedReleaseScript() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation("user-1", "key-1", request(1));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).setIfAbsent(keys.capture(), anyString(), any());
        assertEquals("user-purchase:lock:user-1:event-1", keys.getAllValues().get(0));
        assertEquals("inventory:lock:ticket-1", keys.getAllValues().get(1));
        verify(redisTemplate, times(2)).execute(any(), anyList(), any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void failedFirstLockDoesNotRunBusinessOperationOrReleaseSomeoneElsesLock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> reservationService.createReservation("user-1", "key-1", request(1)));

        verifyNoInteractions(mongoTemplate);
        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }

    @Test
    void initializesOnlyBalancedInventory() {
        InventoryInitializationRequest request = new InventoryInitializationRequest();
        request.setEventId("event-1");
        request.setTicketTypeId("ticket-1");
        request.setTotalQuantity(3);
        request.setAvailableQuantity(3);
        request.setReservedQuantity(0);
        request.setSoldQuantity(0);
        when(inventoryRepository.insert(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(3, reservationService.initializeInventory(request).getAvailableQuantity());
    }

    @Test
    void retriedInventorySetupReturnsCurrentInventoryWithoutAddingStock() {
        Inventory existing = existingInventory("event-1", 3);
        existing.setAvailableQuantity(1);
        existing.setReservedQuantity(1);
        existing.setSoldQuantity(1);
        when(inventoryRepository.insert(any(Inventory.class))).thenThrow(new DuplicateKeyException("ticket-1"));
        when(inventoryRepository.findById("ticket-1")).thenReturn(Optional.of(existing));

        InventoryResponse response = reservationService.initializeInventory(initialSetup("event-1", 3));

        assertEquals(3, response.getTotalQuantity());
        assertEquals(1, response.getAvailableQuantity());
        assertEquals(1, response.getSoldQuantity());
        verify(inventoryRepository, never()).save(any());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void inventorySetupWithDifferentTotalOrEventIsAConflict() {
        when(inventoryRepository.insert(any(Inventory.class))).thenThrow(new DuplicateKeyException("ticket-1"));
        when(inventoryRepository.findById("ticket-1")).thenReturn(Optional.of(existingInventory("event-1", 3)));

        assertThrows(InventoryConflictException.class,
                () -> reservationService.initializeInventory(initialSetup("event-1", 5)));
        assertThrows(InventoryConflictException.class,
                () -> reservationService.initializeInventory(initialSetup("event-2", 3)));
        verify(inventoryRepository, never()).save(any());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void unbalancedInventorySetupIsRejectedBeforePersisting() {
        InventoryInitializationRequest request = initialSetup("event-1", 3);
        request.setAvailableQuantity(4);

        assertThrows(IllegalArgumentException.class, () -> reservationService.initializeInventory(request));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void inventoryForTicketTypeIsReadWithoutModifyingIt() {
        Inventory inventory = existingInventory("event-1", 100);
        inventory.setAvailableQuantity(40);
        inventory.setReservedQuantity(10);
        inventory.setSoldQuantity(50);
        when(inventoryRepository.findById("ticket-1")).thenReturn(Optional.of(inventory));

        InventoryResponse response = reservationService.getInventory("ticket-1");

        assertEquals(100, response.getTotalQuantity());
        assertEquals(40, response.getAvailableQuantity());
        assertEquals(10, response.getReservedQuantity());
        assertEquals(50, response.getSoldQuantity());
        verify(inventoryRepository, never()).save(any());
        verifyNoInteractions(mongoTemplate, redisTemplate);
    }

    @Test
    void missingTicketTypeInventoryIsNotFound() {
        when(inventoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class, () -> reservationService.getInventory("missing"));
    }

    @Test
    void eventInventoryListsEveryTicketTypeOfTheEvent() {
        Inventory vip = existingInventory("event-1", 100);
        Inventory regular = existingInventory("event-1", 500);
        regular.setId("ticket-2");
        regular.setTicketTypeId("ticket-2");
        when(inventoryRepository.findByEventIdOrderByTicketTypeIdAsc("event-1")).thenReturn(List.of(vip, regular));
        when(inventoryRepository.findByEventIdOrderByTicketTypeIdAsc("no-stock")).thenReturn(List.of());

        List<InventoryResponse> responses = reservationService.getInventoryByEventId("event-1");

        assertEquals(List.of("ticket-1", "ticket-2"), responses.stream().map(InventoryResponse::getTicketTypeId).toList());
        assertEquals(500, responses.get(1).getTotalQuantity());
        assertTrue(reservationService.getInventoryByEventId("no-stock").isEmpty());
    }

    private InventoryInitializationRequest initialSetup(String eventId, int total) {
        InventoryInitializationRequest request = new InventoryInitializationRequest();
        request.setEventId(eventId);
        request.setTicketTypeId("ticket-1");
        request.setTotalQuantity(total);
        request.setAvailableQuantity(total);
        request.setReservedQuantity(0);
        request.setSoldQuantity(0);
        return request;
    }

    private Inventory existingInventory(String eventId, int total) {
        Inventory inventory = new Inventory();
        inventory.setId("ticket-1");
        inventory.setTicketTypeId("ticket-1");
        inventory.setEventId(eventId);
        inventory.setTotalQuantity(total);
        inventory.setAvailableQuantity(total);
        inventory.setReservedQuantity(0);
        inventory.setSoldQuantity(0);
        return inventory;
    }

    @Test
    void storesAFingerprintAndReservationReferenceForTheFirstIdempotentRequest() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId("res-1");
            return reservation;
        });

        reservationService.createReservation("user-1", "key-1", request(1));

        ArgumentCaptor<ReservationIdempotency> record = ArgumentCaptor.forClass(ReservationIdempotency.class);
        verify(idempotencyRepository).insert(record.capture());
        assertEquals("user-1", record.getValue().getUserId());
        assertEquals("key-1", record.getValue().getIdempotencyKey());
        assertEquals("res-1", record.getValue().getReservationId());
        assertNotNull(record.getValue().getRequestFingerprint());
    }

    @Test
    void replaysTheOriginalReservationForTheSameUserKeyAndRequest() {
        ReservationIdempotency record = idempotency("user-1", "key-1", request(1), "res-1");
        Reservation reservation = activeReservation("res-1");
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(record));
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.createReservation("user-1", "key-1", request(1));

        assertEquals("res-1", response.getReservationId());
        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(eventSaleEligibilityValidator);
    }

    @Test
    void rejectsAReusedKeyWhenTheRequestFingerprintDiffers() {
        ReservationIdempotency record = idempotency("user-1", "key-1", request(1), "res-1");
        when(idempotencyRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class,
                () -> reservationService.createReservation("user-1", "key-1", request(2)));
        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(eventSaleEligibilityValidator);
    }

    @Test
    void rejectsNonPositiveOrMissingQuantityBeforeAnyLockOrInventoryUpdate() {
        ReservationRequest missingQuantity = request(1);
        missingQuantity.setQuantity(null);
        for (ReservationRequest invalid : List.of(request(-2), request(0), missingQuantity)) {
            assertThrows(IllegalArgumentException.class,
                    () -> reservationService.createReservation("user-1", "key-1", invalid));
        }
        verifyNoInteractions(mongoTemplate, redisTemplate, transactionTemplate, eventSaleEligibilityValidator,
                idempotencyRepository, reservationRepository, reservationOutboxService);
    }

    @Test
    void singleRequestAboveFourTicketLimitIsRejectedWithoutInventoryUpdateOrReservation() {
        for (int quantity : new int[]{5, Integer.MAX_VALUE}) {
            assertThrows(PurchaseLimitExceededException.class,
                    () -> reservationService.createReservation("user-1", "key-" + quantity, request(quantity)));
        }
        verifyNoInteractions(mongoTemplate, reservationOutboxService);
        verify(reservationRepository, never()).save(any());
        verify(idempotencyRepository, never()).insert(any(ReservationIdempotency.class));
    }

    @Test
    void requestThatWouldTakeExistingAllocationAboveFourIsRejected() {
        Reservation existing = activeReservation("res-existing");
        existing.setQuantity(3);
        when(reservationRepository.findByUserIdAndEventIdAndStatusIn(anyString(), anyString(), anyCollection())).thenReturn(List.of(existing));

        assertThrows(PurchaseLimitExceededException.class,
                () -> reservationService.createReservation("user-1", "key-1", request(2)));
        verifyNoInteractions(mongoTemplate);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void requestThatReachesExactlyFourIsAllowed() {
        Reservation existing = activeReservation("res-existing");
        existing.setQuantity(3);
        when(reservationRepository.findByUserIdAndEventIdAndStatusIn(anyString(), anyString(), anyCollection())).thenReturn(List.of(existing));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation("user-1", "key-1", request(1));

        assertInventoryIncrement("reservedQuantity", 1);
    }

    private void assertTransitionStatus(String expectedStatus) {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Reservation.class));
        assertEquals(expectedStatus, update.getValue().getUpdateObject().get("$set", org.bson.Document.class).getString("status"));
    }

    private void assertInventoryIncrement(String field, int expected) {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Inventory.class));
        assertEquals(expected, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).getInteger(field));
    }

    private ReservationRequest request(int quantity) {
        ReservationRequest request = new ReservationRequest();
        request.setEventId("event-1");
        request.setTicketTypeId("ticket-1");
        request.setQuantity(quantity);
        return request;
    }

    private Reservation activeReservation(String id) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUserId("user-1");
        reservation.setEventId("event-1");
        reservation.setTicketTypeId("ticket-1");
        reservation.setQuantity(1);
        reservation.setStatus(ReservationStatus.ACTIVE.name());
        reservation.setExpiresAt(Instant.now().plusSeconds(600));
        return reservation;
    }

    private ReservationIdempotency idempotency(String userId, String key, ReservationRequest request, String reservationId) {
        ReservationIdempotency record = new ReservationIdempotency();
        record.setUserId(userId);
        record.setIdempotencyKey(key);
        record.setReservationId(reservationId);
        record.setRequestFingerprint(fingerprint(request));
        return record;
    }

    private String fingerprint(ReservationRequest request) {
        try {
            String canonical = request.getEventId() + "\n" + request.getTicketTypeId() + "\n" + request.getQuantity();
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private UpdateResult updated() { return UpdateResult.acknowledged(1, 1L, null); }
    private UpdateResult notUpdated() { return UpdateResult.acknowledged(0, 0L, null); }

    private enum ReservationAttempt { RESERVED, INVENTORY_UNAVAILABLE }
}
