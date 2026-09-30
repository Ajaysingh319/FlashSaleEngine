package com.flashsale.order.service;

import com.flashsale.reservation.dto.ReservationResponse;
import com.flashsale.order.exception.ReservationNotFoundException;
import com.flashsale.order.exception.ReservationServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class ReservationServiceClient {

    private final RestTemplate restTemplate;
    private final String reservationServiceUrl;

    public ReservationServiceClient(RestTemplate restTemplate,
                                    @Value("${reservation.service.url}") String reservationServiceUrl) {
        this.restTemplate = restTemplate;
        this.reservationServiceUrl = reservationServiceUrl;
    }

    public ReservationResponse getReservationById(String reservationId) {
        try {
            ResponseEntity<ReservationResponse> response = restTemplate.getForEntity(
                    reservationServiceUrl + "/api/v1/reservations/{id}",
                    ReservationResponse.class,
                    reservationId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new ReservationServiceUnavailableException("Unexpected response from reservation service");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ReservationNotFoundException("Reservation not found with id: " + reservationId);
        } catch (HttpClientErrorException e) {
            throw new ReservationServiceUnavailableException("Reservation service client error: " + e.getStatusCode());
        } catch (HttpServerErrorException e) {
            throw new ReservationServiceUnavailableException("Reservation service server error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new ReservationServiceUnavailableException("Cannot reach reservation service: " + e.getMessage());
        }
    }
}