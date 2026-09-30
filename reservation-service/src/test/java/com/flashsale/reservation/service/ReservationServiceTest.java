package com.flashsale.reservation.service;

import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationStatus;
import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.repository.InventoryRepository;
import com.flashsale.reservation.repository.ReservationRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    @Mock private MongoTemplate mongoTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(reservationRepository.findByUserIdAndEventIdAndStatusIn(anyString(), anyString(), anyCollection())).thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void createsActiveReservationAndAtomicallyMovesInventoryToReserved() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId("res-1");
            return reservation;
        });

        ReservationResponse response = reservationService.createReservation(request("user-1", 2));

        assertEquals("ACTIVE", response.getStatus());
        assertEquals("res-1", response.getId());
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Inventory.class));
        assertEquals(-2, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).getInteger("availableQuantity"));
        assertEquals(2, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).getInteger("reservedQuantity"));
    }

    @Test
    void rejectsReservationWhenAtomicInventoryPredicateDoesNotMatch() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(notUpdated());

        assertThrows(IllegalStateException.class, () -> reservationService.createReservation(request("user-1", 2)));

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
            List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 20)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            reservationService.createReservation(request("user-" + i, 1));
                            return true;
                        } catch (IllegalStateException expected) {
                            return false;
                        }
                    }).toList();
            List<Future<Boolean>> results = pool.invokeAll(attempts);
            assertEquals(1, results.stream().filter(result -> {
                try { return result.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationReleasesReservedInventoryAndSetsCancelled() {
        Reservation active = activeReservation("res-1");
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(active));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Reservation.class))).thenReturn(active);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());

        reservationService.cancelReservation("res-1");

        assertInventoryIncrement("availableQuantity", 1);
        assertInventoryIncrement("reservedQuantity", -1);
        assertTransitionStatus("CANCELLED");
    }

    @Test
    void confirmationMovesReservedInventoryToSold() {
        Reservation active = activeReservation("res-1");
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(active));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Reservation.class))).thenReturn(active);
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
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Reservation.class))).thenReturn(active);
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

        assertThrows(IllegalStateException.class, () -> reservationService.createReservation(request("user-1", 1)));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void acquiresUserLockBeforeInventoryLockAndUsesTokenCheckedReleaseScript() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class))).thenReturn(updated());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation(request("user-1", 1));

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

        assertThrows(IllegalStateException.class, () -> reservationService.createReservation(request("user-1", 1)));

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

    private void assertTransitionStatus(String expectedStatus) {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), eq(Reservation.class));
        assertEquals(expectedStatus, update.getValue().getUpdateObject().get("$set", org.bson.Document.class).getString("status"));
    }

    private void assertInventoryIncrement(String field, int expected) {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Inventory.class));
        assertEquals(expected, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).getInteger(field));
    }

    private ReservationRequest request(String userId, int quantity) {
        ReservationRequest request = new ReservationRequest();
        request.setEventId("event-1");
        request.setTicketTypeId("ticket-1");
        request.setUserId(userId);
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

    private UpdateResult updated() { return UpdateResult.acknowledged(1, 1L, null); }
    private UpdateResult notUpdated() { return UpdateResult.acknowledged(0, 0L, null); }
}
