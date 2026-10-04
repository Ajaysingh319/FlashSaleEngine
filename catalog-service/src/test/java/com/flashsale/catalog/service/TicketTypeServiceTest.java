package com.flashsale.catalog.service;

import com.flashsale.catalog.client.ReservationInventoryClient;
import com.flashsale.catalog.document.Event;
import com.flashsale.catalog.document.InventoryStatus;
import com.flashsale.catalog.document.TicketType;
import com.flashsale.catalog.dto.TicketTypeRequest;
import com.flashsale.catalog.dto.TicketTypeResponse;
import com.flashsale.catalog.exception.EventNotFoundException;
import com.flashsale.catalog.exception.InventoryProvisioningException;
import com.flashsale.catalog.exception.TicketTypeConflictException;
import com.flashsale.catalog.exception.TicketTypeNotFoundException;
import com.flashsale.catalog.repository.EventRepository;
import com.flashsale.catalog.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TicketTypeServiceTest {

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ReservationInventoryClient reservationInventoryClient;

    @InjectMocks
    private TicketTypeService ticketTypeService;

    private Event event;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        event = new Event();
        event.setId("event-1");
        when(eventRepository.findById("event-1")).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEventIdAndName(anyString(), anyString())).thenReturn(Optional.empty());
        when(ticketTypeRepository.insert(any(TicketType.class))).thenAnswer(invocation -> {
            TicketType inserted = invocation.getArgument(0);
            inserted.setId("tt-1");
            return inserted;
        });
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TicketTypeRequest request(String name, double price, int totalQuantity) {
        TicketTypeRequest request = new TicketTypeRequest();
        request.setName(name);
        request.setPrice(price);
        request.setTotalQuantity(totalQuantity);
        request.setEventId("event-1");
        return request;
    }

    private TicketType existing(InventoryStatus status, double price, int totalQuantity) {
        TicketType ticketType = new TicketType();
        ticketType.setId("tt-1");
        ticketType.setName("VIP");
        ticketType.setPrice(price);
        ticketType.setTotalQuantity(totalQuantity);
        ticketType.setInventoryStatus(status);
        ticketType.setEventId("event-1");
        ticketType.setEvent(event);
        ticketType.setCreatedAt(Instant.now());
        ticketType.setUpdatedAt(Instant.now());
        return ticketType;
    }

    // --- Setup: success, retry, conflict, failure ---

    @Test
    void createSavesPendingThenInitializesInventoryThenMarksReady() {
        TicketTypeResponse response = ticketTypeService.createTicketType(request("VIP", 4999.0, 500));

        var order = inOrder(ticketTypeRepository, reservationInventoryClient);
        order.verify(ticketTypeRepository).insert(any(TicketType.class));
        order.verify(reservationInventoryClient).initializeInventory("event-1", "tt-1", 500);
        order.verify(ticketTypeRepository).save(argThat(saved -> saved.getInventoryStatus() == InventoryStatus.READY));

        assertEquals("tt-1", response.getId());
        assertEquals("VIP", response.getName());
        assertEquals(4999.0, response.getPrice());
        assertEquals(500, response.getTotalQuantity());
        assertEquals("READY", response.getInventoryStatus());
        assertEquals("event-1", response.getEventId());
    }

    @Test
    void insertedRecordIsPendingUntilReservationConfirms() {
        doAnswer(invocation -> {
            ArgumentCaptor<TicketType> inserted = ArgumentCaptor.forClass(TicketType.class);
            verify(ticketTypeRepository).insert(inserted.capture());
            assertEquals(InventoryStatus.PENDING, inserted.getValue().getInventoryStatus());
            return null;
        }).when(reservationInventoryClient).initializeInventory(anyString(), anyString(), anyInt());

        ticketTypeService.createTicketType(request("VIP", 4999.0, 500));
    }

    @Test
    void reservationFailureLeavesTicketTypePendingAndIsNotReportedAsSuccess() {
        doThrow(new InventoryProvisioningException("down"))
                .when(reservationInventoryClient).initializeInventory(anyString(), anyString(), anyInt());

        assertThrows(InventoryProvisioningException.class,
                () -> ticketTypeService.createTicketType(request("VIP", 4999.0, 500)));

        verify(ticketTypeRepository).insert(argThat((TicketType t) -> t.getInventoryStatus() == InventoryStatus.PENDING));
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void retryOfPendingSetupCompletesItWithoutCreatingADuplicate() {
        TicketType pending = existing(InventoryStatus.PENDING, 4999.0, 500);
        when(ticketTypeRepository.findByEventIdAndName("event-1", "VIP")).thenReturn(Optional.of(pending));

        TicketTypeResponse response = ticketTypeService.createTicketType(request("VIP", 4999.0, 500));

        verify(ticketTypeRepository, never()).insert(any(TicketType.class));
        verify(reservationInventoryClient).initializeInventory("event-1", "tt-1", 500);
        assertEquals("READY", response.getInventoryStatus());
        assertEquals("tt-1", response.getId());
    }

    @Test
    void retryOfReadySetupReturnsExistingWithoutCallingReservation() {
        when(ticketTypeRepository.findByEventIdAndName("event-1", "VIP"))
                .thenReturn(Optional.of(existing(InventoryStatus.READY, 4999.0, 500)));

        TicketTypeResponse response = ticketTypeService.createTicketType(request("VIP", 4999.0, 500));

        assertEquals("tt-1", response.getId());
        assertEquals("READY", response.getInventoryStatus());
        verifyNoInteractions(reservationInventoryClient);
        verify(ticketTypeRepository, never()).insert(any(TicketType.class));
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void sameNameWithDifferentQuantityOrPriceIsAConflictAndNeverChangesStock() {
        when(ticketTypeRepository.findByEventIdAndName("event-1", "VIP"))
                .thenReturn(Optional.of(existing(InventoryStatus.READY, 4999.0, 500)));

        assertThrows(TicketTypeConflictException.class,
                () -> ticketTypeService.createTicketType(request("VIP", 4999.0, 600)));
        assertThrows(TicketTypeConflictException.class,
                () -> ticketTypeService.createTicketType(request("VIP", 5999.0, 500)));

        verifyNoInteractions(reservationInventoryClient);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void concurrentCreateOfSameTicketTypeContinuesWithTheWinningRecord() {
        TicketType winner = existing(InventoryStatus.PENDING, 4999.0, 500);
        when(ticketTypeRepository.findByEventIdAndName("event-1", "VIP"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(ticketTypeRepository.insert(any(TicketType.class))).thenThrow(new DuplicateKeyException("event_name_unique"));

        TicketTypeResponse response = ticketTypeService.createTicketType(request("VIP", 4999.0, 500));

        assertEquals("tt-1", response.getId());
        assertEquals("READY", response.getInventoryStatus());
        verify(reservationInventoryClient).initializeInventory("event-1", "tt-1", 500);
    }

    @Test
    void concurrentCreateWithDifferentValuesNeverProvisionsEitherRequestsValuesForTheOther() {
        // Request A won the insert with totalQuantity 500; this request (B) asks for 800 under the same name.
        TicketType winnerA = existing(InventoryStatus.PENDING, 4999.0, 500);
        when(ticketTypeRepository.findByEventIdAndName("event-1", "VIP"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerA));
        when(ticketTypeRepository.insert(any(TicketType.class))).thenThrow(new DuplicateKeyException("event_name_unique"));

        assertThrows(TicketTypeConflictException.class,
                () -> ticketTypeService.createTicketType(request("VIP", 4999.0, 800)));

        verifyNoInteractions(reservationInventoryClient);
        verify(ticketTypeRepository, never()).save(any());
        assertEquals(500, winnerA.getTotalQuantity());
        assertEquals(InventoryStatus.PENDING, winnerA.getInventoryStatus());
    }

    @Test
    void retryAfterReservationTimeoutReusesSameTicketTypeAndTotal() {
        // Attempt 1: Reservation times out (it may or may not have created the inventory).
        doThrow(new InventoryProvisioningException("Read timed out"))
                .doNothing()
                .when(reservationInventoryClient).initializeInventory(anyString(), anyString(), anyInt());
        assertThrows(InventoryProvisioningException.class,
                () -> ticketTypeService.createTicketType(request("VIP", 4999.0, 500)));

        ArgumentCaptor<TicketType> inserted = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).insert(inserted.capture());
        TicketType pending = inserted.getValue();
        assertEquals(InventoryStatus.PENDING, pending.getInventoryStatus());

        // Attempt 2: the client resends the same request and finds the PENDING record.
        when(ticketTypeRepository.findByEventIdAndName("event-1", "VIP")).thenReturn(Optional.of(pending));
        TicketTypeResponse response = ticketTypeService.createTicketType(request("VIP", 4999.0, 500));

        verify(ticketTypeRepository, times(1)).insert(any(TicketType.class));
        verify(reservationInventoryClient, times(2)).initializeInventory("event-1", "tt-1", 500);
        assertEquals("tt-1", response.getId());
        assertEquals("READY", response.getInventoryStatus());
    }

    @Test
    void reservationConflictIsSurfacedAndTicketTypeStaysPending() {
        doThrow(new TicketTypeConflictException("different inventory"))
                .when(reservationInventoryClient).initializeInventory(anyString(), anyString(), anyInt());

        assertThrows(TicketTypeConflictException.class,
                () -> ticketTypeService.createTicketType(request("VIP", 4999.0, 500)));
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void createForUnknownEventIsRejectedBeforeAnyWrite() {
        TicketTypeRequest request = request("VIP", 4999.0, 500);
        request.setEventId("missing");
        when(eventRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> ticketTypeService.createTicketType(request));
        verify(ticketTypeRepository, never()).insert(any(TicketType.class));
        verifyNoInteractions(reservationInventoryClient);
    }

    // --- Reads ---

    @Test
    void getTicketTypeByIdReturnsStatus() {
        when(ticketTypeRepository.findById("tt-1")).thenReturn(Optional.of(existing(InventoryStatus.PENDING, 4999.0, 500)));

        TicketTypeResponse response = ticketTypeService.getTicketTypeById("tt-1");

        assertEquals("VIP", response.getName());
        assertEquals(500, response.getTotalQuantity());
        assertEquals("PENDING", response.getInventoryStatus());
    }

    @Test
    void getUnknownTicketTypeThrowsNotFound() {
        when(ticketTypeRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(TicketTypeNotFoundException.class, () -> ticketTypeService.getTicketTypeById("missing"));
    }

    @Test
    void eventListingContainsOnlyReservableTicketTypes() {
        when(ticketTypeRepository.findByEventIdAndInventoryStatus("event-1", InventoryStatus.READY))
                .thenReturn(List.of(existing(InventoryStatus.READY, 4999.0, 500)));

        List<TicketTypeResponse> responses = ticketTypeService.getTicketTypesByEventId("event-1");

        assertEquals(1, responses.size());
        assertEquals("READY", responses.get(0).getInventoryStatus());
        verify(ticketTypeRepository, never()).findByEventId(anyString());
    }

    // --- Update / delete ---

    @Test
    void updateChangesNameAndPriceOnly() {
        when(ticketTypeRepository.findById("tt-1")).thenReturn(Optional.of(existing(InventoryStatus.READY, 4999.0, 500)));

        TicketTypeResponse response = ticketTypeService.updateTicketType("tt-1", request("VIP Gold", 5999.0, 500));

        assertEquals("VIP Gold", response.getName());
        assertEquals(5999.0, response.getPrice());
        assertEquals(500, response.getTotalQuantity());
        verifyNoInteractions(reservationInventoryClient);
    }

    @Test
    void updateCannotChangeTotalQuantityOrEvent() {
        when(ticketTypeRepository.findById("tt-1")).thenReturn(Optional.of(existing(InventoryStatus.READY, 4999.0, 500)));
        TicketTypeRequest otherEvent = request("VIP", 4999.0, 500);
        otherEvent.setEventId("event-2");

        assertThrows(TicketTypeConflictException.class,
                () -> ticketTypeService.updateTicketType("tt-1", request("VIP", 4999.0, 1000)));
        assertThrows(TicketTypeConflictException.class, () -> ticketTypeService.updateTicketType("tt-1", otherEvent));
        verify(ticketTypeRepository, never()).save(any());
        verifyNoInteractions(reservationInventoryClient);
    }

    @Test
    void renamingToAnExistingNameIsAConflict() {
        when(ticketTypeRepository.findById("tt-1")).thenReturn(Optional.of(existing(InventoryStatus.READY, 4999.0, 500)));
        when(ticketTypeRepository.save(any(TicketType.class))).thenThrow(new DuplicateKeyException("event_name_unique"));

        assertThrows(TicketTypeConflictException.class,
                () -> ticketTypeService.updateTicketType("tt-1", request("Regular", 4999.0, 500)));
    }

    @Test
    void updateOfUnknownTicketTypeThrowsNotFound() {
        when(ticketTypeRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(TicketTypeNotFoundException.class,
                () -> ticketTypeService.updateTicketType("missing", request("VIP", 4999.0, 500)));
    }

    @Test
    void testDeleteTicketType() {
        ticketTypeService.deleteTicketType("tt-1");

        verify(ticketTypeRepository, times(1)).deleteById("tt-1");
    }
}
