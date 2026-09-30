package com.flashsale.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class InventoryInitializationRequest {
    @NotBlank private String eventId;
    @NotBlank private String ticketTypeId;
    @NotNull @PositiveOrZero private Integer totalQuantity;
    @NotNull @PositiveOrZero private Integer availableQuantity;
    @NotNull @PositiveOrZero private Integer reservedQuantity;
    @NotNull @PositiveOrZero private Integer soldQuantity;
}
