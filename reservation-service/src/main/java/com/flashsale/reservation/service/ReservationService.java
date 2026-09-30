package com.flashsale.reservation.service;

import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationStatus;
import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.repository.InventoryRepository;
import com.flashsale.reservation.repository.ReservationRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owns reservation state and the authoritative reservation inventory. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    private static final int PURCHASE_LIMIT = 4;
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(30);
    private static final String USER_LOCK_PREFIX = "user-purchase:lock:";
    private static final String INVENTORY_LOCK_PREFIX = "inventory:lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;

    public InventoryResponse initializeInventory(InventoryInitializationRequest request) {
        if (request.getTotalQuantity() != request.getAvailableQuantity() + request.getReservedQuantity() + request.getSoldQuantity()) {
            throw new IllegalArgumentException("Inventory quantities must add up to totalQuantity");
        }
        Inventory inventory = new Inventory();
        inventory.setId(request.getTicketTypeId());
        inventory.setTicketTypeId(request.getTicketTypeId());
        inventory.setEventId(request.getEventId());
        inventory.setTotalQuantity(request.getTotalQuantity());
        inventory.setAvailableQuantity(request.getAvailableQuantity());
        inventory.setReservedQuantity(request.getReservedQuantity());
        inventory.setSoldQuantity(request.getSoldQuantity());
        inventory.setUpdatedAt(Instant.now());
        try {
            return mapToResponse(inventoryRepository.insert(inventory));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Inventory already exists for ticket type " + request.getTicketTypeId(), exception);
        }
    }

    public InventoryResponse getInventory(String ticketTypeId) {
        return inventoryRepository.findById(ticketTypeId).map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));
    }

    public ReservationResponse createReservation(ReservationRequest request) {
        return withLocks(request.getUserId(), request.getEventId(), request.getTicketTypeId(), () -> {
            long alreadyAllocated = reservationRepository.findByUserIdAndEventIdAndStatusIn(request.getUserId(), request.getEventId(),
                            List.of(ReservationStatus.ACTIVE.name(), ReservationStatus.CONFIRMED.name()))
                    .stream().mapToLong(Reservation::getQuantity).sum();
            if (alreadyAllocated + request.getQuantity() > PURCHASE_LIMIT) {
                throw new IllegalStateException("Purchase limit of " + PURCHASE_LIMIT + " tickets per event exceeded");
            }
            if (!reserveInventory(request.getEventId(), request.getTicketTypeId(), request.getQuantity())) {
                throw new IllegalStateException("Insufficient inventory");
            }
            Instant now = Instant.now();
            Reservation reservation = new Reservation();
            reservation.setEventId(request.getEventId());
            reservation.setTicketTypeId(request.getTicketTypeId());
            reservation.setUserId(request.getUserId());
            reservation.setQuantity(request.getQuantity());
            reservation.setStatus(ReservationStatus.ACTIVE.name());
            reservation.setCreatedAt(now);
            reservation.setUpdatedAt(now);
            reservation.setExpiresAt(now.plusSeconds(600));
            return mapToResponse(reservationRepository.save(reservation));
        });
    }

    public ReservationResponse getReservationById(String id) {
        return reservationRepository.findById(id).map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
    }

    public List<ReservationResponse> getReservationsByUserId(String userId) {
        return reservationRepository.findByUserId(userId).stream().map(this::mapToResponse).toList();
    }

    public void confirmReservation(String id) { transitionReservation(id, ReservationStatus.CONFIRMED); }
    public void cancelReservation(String id) { transitionReservation(id, ReservationStatus.CANCELLED); }
    public void expireReservation(String id) { transitionReservation(id, ReservationStatus.EXPIRED); }

    private void transitionReservation(String id, ReservationStatus targetStatus) {
        Reservation current = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        withLocks(current.getUserId(), current.getEventId(), current.getTicketTypeId(), () -> {
            Query activeReservation = new Query(Criteria.where("_id").is(id).and("status").is(ReservationStatus.ACTIVE.name()));
            Update statusUpdate = new Update().set("status", targetStatus.name()).set("updatedAt", Instant.now());
            Reservation transitioned = mongoTemplate.findAndModify(activeReservation, statusUpdate, Reservation.class);
            if (transitioned == null) return null;
            if (targetStatus == ReservationStatus.CONFIRMED) {
                confirmInventory(transitioned.getEventId(), transitioned.getTicketTypeId(), transitioned.getQuantity());
            } else {
                releaseInventory(transitioned.getEventId(), transitioned.getTicketTypeId(), transitioned.getQuantity());
            }
            return null;
        });
    }

    /** Atomic available -> reserved transition. */
    protected boolean reserveInventory(String eventId, String ticketTypeId, int quantity) {
        Query query = new Query(Criteria.where("_id").is(ticketTypeId).and("eventId").is(eventId).and("availableQuantity").gte(quantity));
        Update update = new Update().inc("availableQuantity", -quantity).inc("reservedQuantity", quantity).set("updatedAt", Instant.now());
        return mongoTemplate.updateFirst(query, update, Inventory.class).getModifiedCount() == 1;
    }

    /** Atomic reserved -> available transition. */
    protected void releaseInventory(String eventId, String ticketTypeId, int quantity) {
        Query query = new Query(Criteria.where("_id").is(ticketTypeId).and("eventId").is(eventId).and("reservedQuantity").gte(quantity));
        Update update = new Update().inc("availableQuantity", quantity).inc("reservedQuantity", -quantity).set("updatedAt", Instant.now());
        ensureInventoryUpdated(mongoTemplate.updateFirst(query, update, Inventory.class));
    }

    /** Atomic reserved -> sold transition. */
    protected void confirmInventory(String eventId, String ticketTypeId, int quantity) {
        Query query = new Query(Criteria.where("_id").is(ticketTypeId).and("eventId").is(eventId).and("reservedQuantity").gte(quantity));
        Update update = new Update().inc("reservedQuantity", -quantity).inc("soldQuantity", quantity).set("updatedAt", Instant.now());
        ensureInventoryUpdated(mongoTemplate.updateFirst(query, update, Inventory.class));
    }

    private void ensureInventoryUpdated(UpdateResult result) {
        if (result.getModifiedCount() != 1) throw new IllegalStateException("Inventory state is inconsistent with reservation");
    }

    @Scheduled(fixedDelayString = "${reservation.expiry.check.interval:60000}")
    public void expireReservations() {
        for (Reservation reservation : reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE.name(), Instant.now())) {
            try { expireReservation(reservation.getId()); }
            catch (RuntimeException exception) { log.warn("Could not expire reservation {}", reservation.getId(), exception); }
        }
    }

    private <T> T withLocks(String userId, String eventId, String ticketTypeId, LockedOperation<T> operation) {
        LockHandle userLock = acquireLock(USER_LOCK_PREFIX + userId + ":" + eventId);
        LockHandle inventoryLock = null;
        try {
            inventoryLock = acquireLock(INVENTORY_LOCK_PREFIX + ticketTypeId);
            T result = transactionTemplate.execute(status -> operation.execute());
            releaseLocks(inventoryLock, userLock);
            userLock = null;
            inventoryLock = null;
            return result;
        } finally {
            if (inventoryLock != null) releaseLock(inventoryLock);
            if (userLock != null) releaseLock(userLock);
        }
    }

    private LockHandle acquireLock(String key) {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_LEASE_TIME))) {
            throw new IllegalStateException("Could not acquire reservation lock; please retry");
        }
        return new LockHandle(key, token);
    }

    private void releaseLocks(LockHandle inventoryLock, LockHandle userLock) {
        releaseLock(inventoryLock);
        releaseLock(userLock);
    }

    private void releaseLock(LockHandle lock) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lock.key()), lock.token());
    }

    private ReservationResponse mapToResponse(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getEventId(), reservation.getTicketTypeId(), reservation.getUserId(),
                reservation.getQuantity(), reservation.getStatus(), reservation.getCreatedAt(), reservation.getUpdatedAt(), reservation.getExpiresAt());
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return new InventoryResponse(inventory.getId(), inventory.getEventId(), inventory.getTicketTypeId(), inventory.getTotalQuantity(),
                inventory.getAvailableQuantity(), inventory.getReservedQuantity(), inventory.getSoldQuantity(), inventory.getUpdatedAt());
    }

    @FunctionalInterface private interface LockedOperation<T> { T execute(); }
    private record LockHandle(String key, String token) { }
}
