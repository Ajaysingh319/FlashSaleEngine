package com.flashsale.order.service;

import com.flashsale.order.dto.ReservationResponse;
import com.flashsale.order.exception.ReservationNotFoundException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Component
public class ReservationServiceClient {
    private final RestTemplate restTemplate;
    private final String reservationServiceUrl;
    private final InternalJwtTokenProvider internalJwtTokenProvider;

    public ReservationServiceClient(RestTemplate restTemplate,
                                    @Value("${reservation.service.url}") String reservationServiceUrl,
                                    InternalJwtTokenProvider internalJwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.reservationServiceUrl = reservationServiceUrl;
        this.internalJwtTokenProvider = internalJwtTokenProvider;
    }

    public ReservationResponse getReservationById(String reservationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(internalJwtTokenProvider.token());
        try {
            ResponseEntity<ReservationResponse> response = restTemplate.exchange(
                    reservationServiceUrl + "/internal/v1/reservations/{id}", HttpMethod.GET,
                    new HttpEntity<Void>(headers), ReservationResponse.class, reservationId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ReservationServiceUnavailableException("Reservation service returned an empty response");
            }
            return response.getBody();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ReservationNotFoundException("Reservation not found with id: " + reservationId);
        } catch (HttpClientErrorException exception) {
            throw new ReservationServiceUnavailableException("Reservation service client error: " + exception.getStatusCode());
        } catch (ResourceAccessException exception) {
            throw new ReservationServiceUnavailableException("Cannot reach reservation service: " + exception.getMessage());
        } catch (RestClientException exception) {
            throw new ReservationServiceUnavailableException("Reservation service request failed: " + exception.getMessage());
        }
    }
}
