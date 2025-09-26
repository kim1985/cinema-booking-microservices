package com.cinema.shared.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingCreatedEvent(
        Long bookingId,
        String userEmail,
        String movieTitle,
        LocalDateTime screeningTime,
        Integer numberOfSeats,
        BigDecimal totalPrice
) {}