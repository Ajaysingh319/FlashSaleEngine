package com.flashsale.reservation.service;

import com.flashsale.reservation.document.Inventory;
import com.flashsale.reservation.document.ReservationIdempotency;
import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.document.ReservationStatus;
import com.flashsale.reservation.dto.InventoryInitializationRequest;
import com.flashsale.reservation.dto.InventoryResponse;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.exception.IdempotencyConflictException;
import com.flashsale.reservation.exception.InventoryUnavailableException;
import com.flashsale.reservation.exception.InvalidReservationStateException;
import com.flashsale.reservation.exception.PurchaseLimitExceededException;
import com.flashsale.reservation.exception.ReservationExpiredException;
import com.flashsale.reservation.exception.ReservationNotFoundException;
import com.flashsale.reservation.exception.ReservationOwnershipException;
import com.flashsale.reservation.outbox.ReservationOutboxService;
import com.flashsale.reservation.repository.InventoryRepository;
import com.flashsale.reservation.repository.ReservationIdempotencyRepository;
import com.flashsale.reservation.repository.ReservationRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
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
    private final ReservationIdempotencyRepository idempotencyRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ReservationOutboxService reservationOutboxService;

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

    public ReservationResponse createReservation(String userId, String idempotencyKey, ReservationRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        String requestFingerprint = requestFingerprint(request);
        Optional<ReservationIdempotency> existing = idempotencyRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestFingerprint);
        }

        try {
            return withLocks(userId, request.getEventId(), request.getTicketTypeId(), () -> {
            Optional<ReservationIdempotency> idempotencyInsideTransaction = idempotencyRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (idempotencyInsideTransaction.isPresent()) {
                return replayOrReject(idempotencyInsideTransaction.get(), requestFingerprint);
            }
            long alreadyAllocated = reservationRepository.findByUserIdAndEventIdAndStatusIn(userId, request.getEventId(),
                            List.of(ReservationStatus.ACTIVE.name(), ReservationStatus.CONFIRMED.name()))
                    .stream().mapToLong(Reservation::getQuantity).sum();
            if (alreadyAllocated + request.getQuantity() > PURCHASE_LIMIT) {
                throw new PurchaseLimitExceededException(PURCHASE_LIMIT);
            }
            if (!reserveInventory(request.getEventId(), request.getTicketTypeId(), request.getQuantity())) {
                throw new InventoryUnavailableException();
            }
            Instant now = Instant.now();
            Reservation reservation = new Reservation();
            reservation.setEventId(request.getEventId());
            reservation.setTicketTypeId(request.getTicketTypeId());
            reservation.setUserId(userId);
            reservation.setQuantity(request.getQuantity());
            reservation.setStatus(ReservationStatus.ACTIVE.name());
            reservation.setCreatedAt(now);
            reservation.setUpdatedAt(now);
            reservation.setExpiresAt(now.plusSeconds(600));
            Reservation saved = reservationRepository.save(reservation);
            reservationOutboxService.append("reservation.created", "RESERVATION_CREATED", saved);

            ReservationIdempotency idempotency = new ReservationIdempotency();
            idempotency.setUserId(userId);
            idempotency.setIdempotencyKey(idempotencyKey);
            idempotency.setRequestFingerprint(requestFingerprint);
            idempotency.setReservationId(saved.getId());
            idempotency.setCreatedAt(now);
            idempotencyRepository.insert(idempotency);
            return mapToResponse(saved);
            });
        } catch (DuplicateKeyException exception) {
            ReservationIdempotency persisted = idempotencyRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> exception);
            return replayOrReject(persisted, requestFingerprint);
        }
    }

    private ReservationResponse replayOrReject(ReservationIdempotency idempotency, String requestFingerprint) {
        if (!idempotency.getRequestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException("Idempotency-Key was already used with a different reservation request");
        }
        Reservation reservation = reservationRepository.findById(idempotency.getReservationId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record refers to a missing reservation"));
        return mapToResponse(reservation);
    }

    private String requestFingerprint(ReservationRequest request) {
        String canonicalRequest = request.getEventId() + "\n" + request.getTicketTypeId() + "\n" + request.getQuantity();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public ReservationResponse getReservationById(String id, String userId) {
        Reservation reservation = getReservation(id);
        if (!reservation.getUserId().equals(userId)) {
            throw new ReservationOwnershipException();
        }
        return mapToResponse(reservation);
    }

    public List<ReservationResponse> getReservationsByUserId(String userId) {
        return reservationRepository.findByUserId(userId).stream().map(this::mapToResponse).toList();
    }

    /** Used by trusted downstream services; public callers must use the ownership-checked method. */
    public ReservationResponse getInternalReservationById(String id) {
        return mapToResponse(getReservation(id));
    }

    /** Idempotent internal confirmation for the Order that owns this reservation. */
    public ReservationResponse confirmReservationForOrder(String id, String orderId, String userId) {
        return transitionReservationForOrder(id, orderId, userId, ReservationStatus.CONFIRMED);
    }

    /** Idempotent internal release when the Order's payment has failed. */
    public ReservationResponse cancelReservationAfterPaymentFailure(String id, String orderId, String userId) {
        return transitionReservationForOrder(id, orderId, userId, ReservationStatus.CANCELLED);
    }

    public void confirmReservation(String id) { transitionReservation(id, ReservationStatus.CONFIRMED); }
    public void cancelReservation(String id, String userId) {
        Reservation reservation = getReservation(id);
        if (!reservation.getUserId().equals(userId)) {
            throw new ReservationOwnershipException();
        }
        transitionReservation(reservation, ReservationStatus.CANCELLED);
    }
    public void expireReservation(String id) { transitionReservation(id, ReservationStatus.EXPIRED); }

    private ReservationResponse transitionReservationForOrder(String id, String orderId, String userId,
                                                               ReservationStatus targetStatus) {
        if (orderId == null || orderId.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("orderId and userId are required");
        }
        Reservation current = getReservation(id);
        if (!current.getUserId().equals(userId)) throw new ReservationOwnershipException();
        OrderTransitionResult result = withLocks(userId, current.getEventId(), current.getTicketTypeId(), () -> {
            Reservation latest = getReservation(id);
            if (!latest.getUserId().equals(userId)) throw new ReservationOwnershipException();
            ensureOrderAssociation(latest, orderId);
            if (targetStatus.name().equals(latest.getStatus())) {
                if (!orderId.equals(latest.getOrderId())) {
                    throw new InvalidReservationStateException("Reservation is not associated with this order");
                }
                return new OrderTransitionResult(mapToResponse(latest), false);
            }
            if (!ReservationStatus.ACTIVE.name().equals(latest.getStatus())) {
                throw new InvalidReservationStateException("Only ACTIVE reservations can be " + targetStatus.name().toLowerCase());
            }
            Instant now = Instant.now();
            if (!latest.getExpiresAt().isAfter(now)) {
                applyTransition(latest, ReservationStatus.EXPIRED, now);
                return new OrderTransitionResult(null, true);
            }
            Reservation transitioned = applyOrderTransition(latest, orderId, targetStatus, now);
            if (transitioned == null) {
                throw new InvalidReservationStateException("Reservation state changed while processing the order");
            }
            return new OrderTransitionResult(mapToResponse(transitioned), false);
        });
        if (result.expired()) throw new ReservationExpiredException();
        return result.response();
    }

    private void transitionReservation(String id, ReservationStatus targetStatus) {
        transitionReservation(getReservation(id), targetStatus);
    }

    private void transitionReservation(Reservation current, ReservationStatus targetStatus) {
        boolean expiredBeforeRequestedTransition = Boolean.TRUE.equals(withLocks(
                current.getUserId(), current.getEventId(), current.getTicketTypeId(), () -> {
            Reservation latest = getReservation(current.getId());
            if (!ReservationStatus.ACTIVE.name().equals(latest.getStatus())) {
                if (targetStatus == ReservationStatus.EXPIRED) return null;
                throw new InvalidReservationStateException("Only ACTIVE reservations can be " + targetStatus.name().toLowerCase());
            }
            Instant now = Instant.now();
            if (targetStatus != ReservationStatus.EXPIRED && !latest.getExpiresAt().isAfter(now)) {
                applyTransition(latest, ReservationStatus.EXPIRED, now);
                return true;
            }
            if (targetStatus == ReservationStatus.EXPIRED && latest.getExpiresAt().isAfter(now)) {
                return null;
            }
            applyTransition(latest, targetStatus, now);
            return null;
        }));
        if (expiredBeforeRequestedTransition) {
            throw new ReservationExpiredException();
        }
    }

    private void applyTransition(Reservation latest, ReservationStatus targetStatus, Instant now) {
        Query activeReservation = new Query(Criteria.where("_id").is(latest.getId()).and("status").is(ReservationStatus.ACTIVE.name()));
        Update statusUpdate = new Update().set("status", targetStatus.name()).set("updatedAt", now);
        Reservation transitioned = mongoTemplate.findAndModify(activeReservation, statusUpdate,
                FindAndModifyOptions.options().returnNew(true), Reservation.class);
        if (transitioned == null) {
            return;
        }
        if (targetStatus == ReservationStatus.CONFIRMED) {
            confirmInventory(transitioned.getEventId(), transitioned.getTicketTypeId(), transitioned.getQuantity());
        } else {
            releaseInventory(transitioned.getEventId(), transitioned.getTicketTypeId(), transitioned.getQuantity());
        }
        reservationOutboxService.append(eventTopic(targetStatus), eventType(targetStatus), transitioned);
    }

    private Reservation applyOrderTransition(Reservation latest, String orderId, ReservationStatus targetStatus, Instant now) {
        Query activeReservation = new Query(Criteria.where("_id").is(latest.getId())
                .and("status").is(ReservationStatus.ACTIVE.name()));
        Update statusUpdate = new Update().set("status", targetStatus.name()).set("orderId", orderId).set("updatedAt", now);
        Reservation transitioned = mongoTemplate.findAndModify(activeReservation, statusUpdate,
                FindAndModifyOptions.options().returnNew(true), Reservation.class);
        if (transitioned == null) return null;
        if (targetStatus == ReservationStatus.CONFIRMED) {
            confirmInventory(transitioned.getEventId(), transitioned.getTicketTypeId(), transitioned.getQuantity());
        } else {
            releaseInventory(transitioned.getEventId(), transitioned.getTicketTypeId(), transitioned.getQuantity());
        }
        reservationOutboxService.append(eventTopic(targetStatus), eventType(targetStatus), transitioned);
        return transitioned;
    }

    private void ensureOrderAssociation(Reservation reservation, String orderId) {
        if (reservation.getOrderId() != null && !reservation.getOrderId().equals(orderId)) {
            throw new InvalidReservationStateException("Reservation is already associated with another order");
        }
    }

    private Reservation getReservation(String id) {
        return reservationRepository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }

    private String eventTopic(ReservationStatus status) {
        return "reservation." + status.name().toLowerCase();
    }

    private String eventType(ReservationStatus status) {
        return "RESERVATION_" + status.name();
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
    private record OrderTransitionResult(ReservationResponse response, boolean expired) { }
}
