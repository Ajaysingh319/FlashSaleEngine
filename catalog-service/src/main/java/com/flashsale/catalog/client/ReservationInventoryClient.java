package com.flashsale.catalog.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flashsale.catalog.exception.InventoryProvisioningException;
import com.flashsale.catalog.exception.TicketTypeConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Initializes a ticket type's inventory in Reservation Service, the inventory authority (PRD 6.4, TDD 5),
 * via POST /internal/v1/inventory with an INTERNAL service token. Reservation treats a repeat of the same
 * setup as a no-op replay, so this call is safe to retry.
 */
@Component
public class ReservationInventoryClient {
    private final RestTemplate restTemplate;
    private final InternalJwtTokenProvider tokenProvider;
    private final String reservationServiceUrl;

    public ReservationInventoryClient(RestTemplate restTemplate, InternalJwtTokenProvider tokenProvider,
                                      @Value("${reservation.service.url}") String reservationServiceUrl) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.reservationServiceUrl = reservationServiceUrl;
    }

    /**
     * @throws TicketTypeConflictException     if Reservation already holds different inventory for this ticket type
     * @throws InventoryProvisioningException  if Reservation is unreachable, fails, or returns an unexpected response
     */
    public void initializeInventory(String eventId, String ticketTypeId, int totalQuantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.token());
        InventorySetup body = new InventorySetup(eventId, ticketTypeId, totalQuantity, totalQuantity, 0, 0);

        ResponseEntity<InventorySetupResult> response;
        try {
            response = restTemplate.postForEntity(reservationServiceUrl + "/internal/v1/inventory",
                    new HttpEntity<>(body, headers), InventorySetupResult.class);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new TicketTypeConflictException(
                    "Reservation Service already holds different inventory for ticket type " + ticketTypeId, exception);
        } catch (RestClientException exception) {
            throw new InventoryProvisioningException(
                    "Could not initialize inventory for ticket type " + ticketTypeId + " in Reservation Service", exception);
        }

        InventorySetupResult result = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || result == null
                || !ticketTypeId.equals(result.ticketTypeId()) || !eventId.equals(result.eventId())
                || result.totalQuantity() == null || result.totalQuantity() != totalQuantity) {
            throw new InventoryProvisioningException(
                    "Reservation Service returned an unexpected inventory response for ticket type " + ticketTypeId);
        }
    }

    /** Request body of Reservation's InventoryInitializationRequest. */
    record InventorySetup(String eventId, String ticketTypeId, int totalQuantity,
                          int availableQuantity, int reservedQuantity, int soldQuantity) { }

    /** Fields of Reservation's InventoryResponse used to confirm the setup. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InventorySetupResult(String eventId, String ticketTypeId, Integer totalQuantity) { }
}
