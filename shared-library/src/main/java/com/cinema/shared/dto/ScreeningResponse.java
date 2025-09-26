package com.cinema.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScreeningResponse(
        Long id,
        Long movieId,
        String movieTitle,
        LocalDateTime startTime,
        Integer availableSeats,
        BigDecimal price
) {}