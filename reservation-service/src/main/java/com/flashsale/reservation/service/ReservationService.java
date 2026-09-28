package com.flashsale.reservation.service;

import com.flashsale.reservation.document.Reservation;
import com.flashsale.reservation.dto.ReservationRequest;
import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.reservation.repository.ReservationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final String LOCK_PREFIX = "lock:reservation:";
    private static final Long LOCK_TIMEOUT_MS = 3000L; // 3 seconds to acquire lock
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(30); // lock auto-expire after 30 sec

    private final ReservationRepository reservationRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    // In real implementation, we would have a client to ticket-type service to get and update inventory
    // For this MVP, we assume inventory is managed via a separate service; we'll mock in tests.
    // We'll define a method to update inventory that will be overridden in tests or implemented via Feign/restTemplate.

    /**
     * Create a reservation for a ticket type.
     * Uses Redis distributed lock to prevent overselling.
     * Steps:
     * 1. Acquire lock for the specific event-ticketType combination.
     * 2. Check if sufficient inventory exists (via ticket-type service or direct inventory read).
     * 3. If sufficient, atomically decrement availableQuantity and increment reservedQuantity.
     * 4. Create reservation document with status PENDING and expiration time (now + TTL).
     * 5. Release lock.
     * 6. Send Kafka event (reservation created) - omitted for brevity.
     *
     * @param request reservation request
     * @return reservation response
     */
    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        String lockKey = LOCK_PREFIX + request.getEventId() + ":" + request.getTicketTypeId();
        Boolean lockAcquired = false;
        try {
            lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, UUID.randomUUID().toString(), LOCK_LEASE_TIME);
            if (Boolean.FALSE.equals(lockAcquired)) {
                throw new RuntimeException("Could not acquire lock, please retry");
            }

            // TODO: Call ticket-type service to get current inventory and validate
            // For now, we simulate inventory check and update via a hypothetical method.
            // In a real implementation, we would use a Feign client to ticket-type service.
            boolean sufficient = checkAndUpdateInventory(request.getEventId(), request.getTicketTypeId(), request.getQuantity());
            if (!sufficient) {
                throw new RuntimeException("Insufficient inventory");
            }

            // Create reservation
            Reservation reservation = new Reservation();
            reservation.setEventId(request.getEventId());
            reservation.setTicketTypeId(request.getTicketTypeId());
            reservation.setUserId(request.getUserId());
            reservation.setQuantity(request.getQuantity());
            reservation.setStatus("PENDING");
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            // Reservation TTL: 10 minutes as per BR-003
            reservation.setExpiresAt(Instant.now().plusSeconds(600));

            Reservation saved = reservationRepository.save(reservation);

            // TODO: Send Kafka event "ReservationCreated"

            return mapToResponse(saved);
        } finally {
            if (Boolean.TRUE.equals(lockAcquired)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    /**
     * Check and update inventory atomically.
     * In a real system, this would be a call to ticket-type service that performs
     * an atomic update on the ticket type document (decrement available, increment reserved).
     * For this MVP, we'll implement a simple version using MongoTemplate on a hypothetical
     * ticket type collection. Since we don't have the ticket type document here, we'll
     * leave it as a stub to be mocked in tests.
     *
     * @param eventId event id
     * @param ticketTypeId ticket type id
     * @param quantity quantity to reserve
     * @return true if sufficient inventory and update succeeded
     */
    protected boolean checkAndUpdateInventory(String eventId, String ticketTypeId, Integer quantity) {
        // This is a stub. In actual implementation, we would:
        // 1. Query the ticket type document for the given eventId and ticketTypeId to get availableQuantity.
        // 2. If availableQuantity >= quantity, then update:
        //    availableQuantity = availableQuantity - quantity
        //    reservedQuantity = reservedQuantity + quantity
        // 3. Perform this update atomically (using a single MongoDB update operation with inc).
        // 4. Return true if the update matched a document (i.e., sufficient inventory existed).
        // 5. Otherwise return false.
        //
        // Since we don't have the ticket type service or document in this service,
        // we will rely on mocks in the unit tests to simulate this behavior.
        // For the purpose of making the code compile, we return true.
        return true;
    }

    public ReservationResponse getReservationById(String id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        return mapToResponse(reservation);
    }

    public List<ReservationResponse> getReservationsByUserId(String userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Cancel a reservation and release inventory.
     * Steps:
     * 1. Acquire lock for the event-ticketType to prevent race conditions with concurrent reservations/expiry.
     * 2. Find reservation by id and ensure it is in a cancellable state (PENDING).
     * 3. Update reservation status to CANCELLED.
     * 4. Release inventory: increment availableQuantity and decrement reservedQuantity for the ticket type.
     * 5. Release lock.
     * 6. Send Kafka event "ReservationCancelled".
     *
     * @param id reservation id
     */
    @Transactional
    public void cancelReservation(String id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        if (!"PENDING".equals(reservation.getStatus())) {
            throw new RuntimeException("Reservation cannot be cancelled");
        }

        String lockKey = LOCK_PREFIX + reservation.getEventId() + ":" + reservation.getTicketTypeId();
        Boolean lockAcquired = false;
        try {
            lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, UUID.randomUUID().toString(), LOCK_LEASE_TIME);
            if (Boolean.FALSE.equals(lockAcquired)) {
                throw new RuntimeException("Could not acquire lock for cancellation");
            }

            // Update reservation status
            reservation.setStatus("CANCELLED");
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);

            // Release inventory
            releaseInventory(reservation.getEventId(), reservation.getTicketTypeId(), reservation.getQuantity());

            // TODO: Send Kafka event
        } finally {
            if (Boolean.TRUE.equals(lockAcquired)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    protected void releaseInventory(String eventId, String ticketTypeId, Integer quantity) {
        // Stub: in real implementation, call ticket-type service to atomically increment available and decrement reserved.
        // For now, do nothing.
    }

    /**
     * Find expired reservations and release inventory.
     * This method is scheduled to run periodically.
     */
    @Scheduled(fixedDelayString = "${reservation.expiry.check.interval:60000}") // every 60 seconds by default
    public void expireReservations() {
        Instant now = Instant.now();
        // Find reservations that are PENDING and expired
        // We'll use a simple query; in production, consider indexing on expiresAt and status.
        // For simplicity, we fetch all PENDING and filter by expiresAt (could be optimized).
        // Since we expect limited expired reservations at any time, this is acceptable.
        reservationRepository.findAll().forEach(reservation -> {
            if ("PENDING".equals(reservation.getStatus()) &&
                    reservation.getExpiresAt().isBefore(now)) {
                try {
                    cancelReservation(reservation.getId());
                } catch (Exception e) {
                    // Log error but continue
                    System.err.println("Failed to expire reservation " + reservation.getId() + ": " + e.getMessage());
                }
            }
        });
    }

    private ReservationResponse mapToResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getTicketTypeId(),
                reservation.getUserId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                reservation.getExpiresAt()
        );
    }
}