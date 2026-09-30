package com.flashsale.order.service;

import com.flashsale.order.dto.OrderRequest;
import com.flashsale.order.dto.OrderResponse;
import com.flashsale.order.exception.*;
import com.flashsale.order.repository.IdempotencyRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.OutboxEventRepository;
import com.flashsale.order.repository.ProcessedEventRepository;
import com.flashsale.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderServiceTest {

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private IdempotencyRepository idempotencyRepository;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private ProcessedEventRepository processedEventRepository;

    @MockBean
    private ReservationServiceClient reservationServiceClient;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest validRequest;
    private String userId = "user1";
    private String reservationId = "res1";
    private String eventId = "event1";
    private String ticketTypeId = "type1";
    private int quantity = 2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validRequest = new OrderRequest();
        validRequest.setReservationId(reservationId);
        validRequest.setEventId(eventId);
        validRequest.setTicketTypeId(ticketTypeId);
        validRequest.setQuantity(quantity);
    }

    @Test
    void testCreateOrder_Success() {
        // Mock reservation response
        ReservationResponse reservation = new ReservationResponse();
        reservation.setId(reservationId);
        reservation.setEventId(eventId);
        reservation.setTicketTypeId(ticketTypeId);
        reservation.setUserId(userId);
        reservation.setQuantity(quantity);
        reservation.setStatus("PENDING");
        reservation.setExpiresAt(Instant.now().plusSeconds(600)); // future

        when(reservationServiceClient.getReservationById(eq(reservationId)))
                .thenReturn(reservation);
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(userId, validRequest);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(reservationId, response.getReservationId());
        assertEquals(eventId, response.getEventId());
        assertEquals(ticketTypeId, response.getTicketTypeId());
        assertEquals(quantity, response.getQuantity());
        // amount is 0.0 placeholder
        assertEquals(0.0, response.getAmount());
        assertEquals("PENDING_PAYMENT", response.getStatus());
        verify(orderRepository).save(any());
    }

    @Test
    void testCreateOrder_ReservationNotFound() {
        when(reservationServiceClient.getReservationById(eq(reservationId)))
                .thenThrow(new ReservationNotFoundException("Reservation not found"));

        assertThrows(ReservationNotFoundException.class, () ->
                orderService.createOrder(userId, validRequest));
    }

    @Test
    void testCreateOrder_WrongUser() {
        ReservationResponse reservation = new ReservationResponse();
        reservation.setId(reservationId);
        reservation.setEventId(eventId);
        reservation.setTicketTypeId(ticketTypeId);
        reservation.setUserId("otherUser"); // different user
        reservation.setQuantity(quantity);
        reservation.setStatus("PENDING");
        reservation.setExpiresAt(Instant.now().plusSeconds(600));

        when(reservationServiceClient.getReservationById(eq(reservationId)))
                .thenReturn(reservation);

        assertThrows(ReservationOwnershipException.class, () ->
                orderService.createOrder(userId, validRequest));
    }

    @Test
    void testCreateOrder_ReservationNotActive() {
        ReservationResponse reservation = new ReservationResponse();
        reservation.setId(reservationId);
        reservation.setEventId(eventId);
        reservation.setTicketTypeId(ticketTypeId);
        reservation.setUserId(userId);
        reservation.setQuantity(quantity);
        reservation.setStatus("CANCELLED"); // not PENDING
        reservation.setExpiresAt(Instant.now().plusSeconds(600));

        when(reservationServiceClient.getReservationById(eq(reservationId)))
                .thenReturn(reservation);

        assertThrows(ReservationNotActiveException.class, () ->
                orderService.createOrder(userId, validRequest));
    }

    @Test
    void testCreateOrder_ReservationExpired() {
        ReservationResponse reservation = new ReservationResponse();
        reservation.setId(reservationId);
        reservation.setEventId(eventId);
        reservation.setTicketTypeId(ticketTypeId);
        reservation.setUserId(userId);
        reservation.setQuantity(quantity);
        reservation.setStatus("PENDING");
        reservation.setExpiresAt(Instant.now().minusSeconds(10)); // expired

        when(reservationServiceClient.getReservationById(eq(reservationId)))
                .thenReturn(reservation);

        assertThrows(ReservationExpiredException.class, () ->
                orderService.createOrder(userId, validRequest));
    }

    @Test
    void testGetOrder_Success() {
        com.flashsale.order.document.Order order = new com.flashsale.order.document.Order();
        order.setOrderId("order1");
        order.setUserId(userId);
        order.setReservationId(reservationId);
        order.setEventId(eventId);
        order.setTicketTypeId(ticketTypeId);
        order.setQuantity(quantity);
        order.setAmount(0.0);
        order.setStatus(com.flashsale.order.document.OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findById(eq("order1"))).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder("order1", userId);

        assertNotNull(response);
        assertEquals("order1", response.getOrderId());
        assertEquals(userId, response.getUserId());
    }

    @Test
    void testGetOrder_NotFound() {
        when(orderRepository.findById(eq("order1"))).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () ->
                orderService.getOrder("order1", userId));
    }

    @Test
    void testGetOrder_Unauthorized() {
        com.flashsale.order.document.Order order = new com.flashsale.order.document.Order();
        order.setOrderId("order1");
        order.setUserId("otherUser"); // different user
        order.setReservationId(reservationId);
        order.setEventId(eventId);
        order.setTicketTypeId(ticketTypeId);
        order.setQuantity(quantity);
        order.setAmount(0.0);
        order.setStatus(com.flashsale.order.document.OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findById(eq("order1"))).thenReturn(Optional.of(order));

        assertThrows(UnauthorizedOrderAccessException.class, () ->
                orderService.getOrder("order1", userId));
    }

    @Test
    void testGetMyOrders() {
        com.flashsale.order.document.Order order1 = new com.flashsale.order.document.Order();
        order1.setOrderId("order1");
        order1.setUserId(userId);
        order1.setReservationId(reservationId);
        order1.setEventId(eventId);
        order1.setTicketTypeId(ticketTypeId);
        order1.setQuantity(quantity);
        order1.setAmount(0.0);
        order1.setStatus(com.flashsale.order.document.OrderStatus.PENDING_PAYMENT);
        order1.setCreatedAt(Instant.now());
        order1.setUpdatedAt(Instant.now());

        com.flashsale.order.document.Order order2 = new com.flashsale.order.document.Order();
        order2.setOrderId("order2");
        order2.setUserId(userId);
        order2.setReservationId("res2");
        order2.setEventId(eventId);
        order2.setTicketTypeId(ticketTypeId);
        order2.setQuantity(1);
        order2.setAmount(0.0);
        order2.setStatus(com.flashsale.order.document.OrderStatus.PENDING_PAYMENT);
        order2.setCreatedAt(Instant.now());
        order2.setUpdatedAt(Instant.now());

        when(orderRepository.findByUserId(eq(userId))).thenReturn(List.of(order1, order2));

        List<OrderResponse> responses = orderService.getMyOrders(userId);

        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(o -> o.getOrderId().equals("order1")));
        assertTrue(responses.stream().anyMatch(o -> o.getOrderId().equals("order2")));
    }

    @Configuration
    static class TestConfig {
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }
}