package com.flashsale.order.exception;

public class ReservationAlreadyUsedException extends RuntimeException { public ReservationAlreadyUsedException(String reservationId) { super("Reservation has already been used by another order: " + reservationId); } }
