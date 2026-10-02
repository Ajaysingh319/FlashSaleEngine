package com.flashsale.reservation.service;

import com.flashsale.reservation.dto.CatalogEventResponse;
import com.flashsale.reservation.exception.CatalogUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/** Reads event metadata from Catalog Service, the source of truth for events and sale windows. */
@Component
public class CatalogServiceClient {
    private final RestTemplate restTemplate;
    private final String catalogServiceUrl;

    public CatalogServiceClient(RestTemplate restTemplate, @Value("${catalog.service.url}") String catalogServiceUrl) {
        this.restTemplate = restTemplate;
        this.catalogServiceUrl = catalogServiceUrl;
    }

    /**
     * Returns the event, or empty when Catalog reports it does not exist (404).
     *
     * @throws CatalogUnavailableException if Catalog cannot be reached, fails, or returns an unusable event
     */
    public Optional<CatalogEventResponse> findEvent(String eventId) {
        ResponseEntity<CatalogEventResponse> response;
        try {
            response = restTemplate.getForEntity(
                    catalogServiceUrl + "/api/v1/events/{eventId}", CatalogEventResponse.class, eventId);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new CatalogUnavailableException("Could not verify event " + eventId + " with Catalog Service", exception);
        }

        CatalogEventResponse event = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || event == null || !eventId.equals(event.getId())
                || event.getStatus() == null || event.getSaleStartTime() == null || event.getSaleEndTime() == null) {
            throw new CatalogUnavailableException("Catalog Service returned an invalid response for event " + eventId);
        }
        return Optional.of(event);
    }
}
