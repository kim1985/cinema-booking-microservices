package com.cinema.shared.dto;

import com.cinema.shared.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long screeningId,
        String userEmail,
        Integer numberOfSeats,
        BigDecimal totalPrice,
        BookingStatus status,
        LocalDateTime createdAt,
        String movieTitle,
        LocalDateTime screeningTime
) {
    // Pattern Matching per messaggi status - Java 21
    public String getStatusMessage() {
        return switch (status) {
            case PENDING -> "Prenotazione in elaborazione...";
            case CONFIRMED -> "Prenotazione confermata per %s!".formatted(movieTitle);
            case CANCELLED -> "Prenotazione per %s cancellata".formatted(movieTitle);
            case EXPIRED -> "Prenotazione per %s scaduta".formatted(movieTitle);
        };
    }
}