package com.flashsale.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    @NotBlank
    private String eventId;

    @NotBlank
    private String ticketTypeId;

    @NotNull
    @Positive
    private Integer quantity;

    @NotBlank
    private String userId;
}