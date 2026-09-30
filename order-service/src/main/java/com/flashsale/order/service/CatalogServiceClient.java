package com.flashsale.order.service;

import com.flashsale.order.dto.TicketTypePriceResponse;
import com.flashsale.order.exception.CatalogPriceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/** Reads the existing Catalog ticket-type price contract and snapshots it in the order. */
@Component
public class CatalogServiceClient {
    private final RestTemplate restTemplate;
    private final String catalogServiceUrl;

    public CatalogServiceClient(RestTemplate restTemplate, @Value("${catalog.service.url}") String catalogServiceUrl) {
        this.restTemplate = restTemplate;
        this.catalogServiceUrl = catalogServiceUrl;
    }

    public BigDecimal getUnitPrice(String eventId, String ticketTypeId) {
        try {
            ResponseEntity<TicketTypePriceResponse> response = restTemplate.getForEntity(
                    catalogServiceUrl + "/api/v1/ticket-types/{id}", TicketTypePriceResponse.class, ticketTypeId);
            TicketTypePriceResponse ticketType = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || ticketType == null || ticketType.getPrice() == null
                    || ticketType.getPrice() <= 0 || !ticketTypeId.equals(ticketType.getId())
                    || !eventId.equals(ticketType.getEventId())) {
                throw new CatalogPriceUnavailableException("Catalog did not return a valid price for ticket type " + ticketTypeId);
            }
            return BigDecimal.valueOf(ticketType.getPrice());
        } catch (CatalogPriceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CatalogPriceUnavailableException("Could not obtain the ticket price from Catalog Service");
        }
    }
}
