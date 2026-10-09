package com.flashsale.reservation.concurrency;

import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.dto.CatalogEventResponse;
import com.flashsale.reservation.dto.CatalogTicketTypeResponse;
import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.InventoryTotals;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.exception.InventoryUnavailableException;
import com.flashsale.reservation.exception.ReservationBusyException;
import com.flashsale.reservation.outbox.ReservationOutboxPublisher;
import com.flashsale.reservation.repository.InventoryRepository;
import com.flashsale.reservation.service.CatalogServiceClient;
import com.flashsale.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Flash-sale concurrency against real MongoDB (replica set, so transactions run) and Redis (PRD 11, TDD 71, 73, 74).
 * Each buyer behaves like a real client: on 503 RESERVATION_BUSY it retries after a short random pause, and it
 * stops once tickets are sold out. Only Catalog (another service) is stubbed, and the Kafka outbox publisher is
 * disabled because these tests are about inventory, not events.
 * <p>
 * Needs Docker; run on demand with {@code mvn verify -Pconcurrency-tests}.
 */
@SpringBootTest
@Testcontainers
class ReservationConcurrencyIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private enum Outcome { RESERVED, SOLD_OUT }

    @MockBean
    private CatalogServiceClient catalogServiceClient;

    @MockBean
    private ReservationOutboxPublisher reservationOutboxPublisher;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @ParameterizedTest(name = "{1} concurrent buyers for {0} tickets")
    @CsvSource({"100, 5000", "10, 1000"})
    void concurrentBuyersNeverOversell(int tickets, int buyers) {
        SaleUnderTest sale = openSale(tickets);

        List<Outcome> outcomes = StartingGate.releaseAll(buyers, buyer -> buyOneTicket(sale, "buyer-" + buyer));

        assertEquals(tickets, outcomes.stream().filter(Outcome.RESERVED::equals).count(), "every ticket is sold");
        assertEquals(buyers - tickets, outcomes.stream().filter(Outcome.SOLD_OUT::equals).count());
        InventoryTotals inventory = inventoryOf(sale);
        assertEquals(new InventoryTotals(tickets, 0, tickets, 0), inventory);
        assertEquals(0, inventory.oversold());
        assertTrue(inventory.consistent(), "total = available + reserved + sold");
        assertEquals(tickets, reservationsOf(sale));
    }

    @Test
    void identicalRequestsWithOneIdempotencyKeyCreateOneReservation() {
        SaleUnderTest sale = openSale(10);

        List<String> reservationIds = StartingGate.releaseAll(100,
                attempt -> reserveRetryingWhileBusy(sale, "same-buyer", "same-key").getReservationId());

        assertEquals(1, Set.copyOf(reservationIds).size(), "every replay returns the one logical reservation");
        assertEquals(new InventoryTotals(10, 9, 1, 0), inventoryOf(sale));
        assertEquals(1, reservationsOf(sale));
    }

    private Outcome buyOneTicket(SaleUnderTest sale, String userId) {
        try {
            reserveRetryingWhileBusy(sale, userId, "key-" + userId);
            return Outcome.RESERVED;
        } catch (InventoryUnavailableException soldOut) {
            return Outcome.SOLD_OUT;
        }
    }

    private ReservationResponse reserveRetryingWhileBusy(SaleUnderTest sale, String userId, String idempotencyKey) {
        while (true) {
            try {
                return reservationService.createReservation(userId, idempotencyKey, sale.oneTicket());
            } catch (ReservationBusyException busy) {
                pauseBeforeRetry();
            }
        }
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 20));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /** A fresh event and ticket type per test, currently on sale in Catalog, with {@code tickets} in stock. */
    private SaleUnderTest openSale(int tickets) {
        SaleUnderTest sale = new SaleUnderTest("event-" + UUID.randomUUID(), "ticket-type-" + UUID.randomUUID());
        Instant now = Instant.now();
        when(catalogServiceClient.findEvent(sale.eventId())).thenReturn(Optional.of(new CatalogEventResponse(
                sale.eventId(), "ON_SALE", now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)))));
        when(catalogServiceClient.findTicketType(sale.ticketTypeId()))
                .thenReturn(Optional.of(new CatalogTicketTypeResponse(sale.ticketTypeId(), sale.eventId(), 4999.0)));

        InventoryInitializationRequest stock = new InventoryInitializationRequest();
        stock.setEventId(sale.eventId());
        stock.setTicketTypeId(sale.ticketTypeId());
        stock.setTotalQuantity(tickets);
        stock.setAvailableQuantity(tickets);
        stock.setReservedQuantity(0);
        stock.setSoldQuantity(0);
        reservationService.initializeInventory(stock);
        return sale;
    }

    private InventoryTotals inventoryOf(SaleUnderTest sale) {
        return InventoryTotals.of(inventoryRepository.findById(sale.ticketTypeId()).orElseThrow());
    }

    private long reservationsOf(SaleUnderTest sale) {
        return mongoTemplate.count(Query.query(where("ticketTypeId").is(sale.ticketTypeId())), Reservation.class);
    }

    private record SaleUnderTest(String eventId, String ticketTypeId) {
        ReservationRequest oneTicket() {
            return new ReservationRequest(eventId, ticketTypeId, 1);
        }
    }
}
