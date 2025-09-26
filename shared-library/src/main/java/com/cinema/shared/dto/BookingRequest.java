package com.cinema.shared.dto;

import jakarta.validation.constraints.*;

public record BookingRequest(
        @NotNull(message = "Screening ID obbligatorio")
        @Min(value = 1, message = "Screening ID deve essere positivo")
        Long screeningId,

        @NotBlank(message = "Email obbligatoria")
        @Email(message = "Email non valida")
        String userEmail,

        @NotNull(message = "Numero posti obbligatorio")
        @Min(value = 1, message = "Minimo 1 posto")
        @Max(value = 10, message = "Massimo 10 posti")
        Integer numberOfSeats
) {
    // Compact constructor per normalizzazione
    public BookingRequest {
        if (userEmail != null) {
            userEmail = userEmail.trim().toLowerCase();
        }
    }
}