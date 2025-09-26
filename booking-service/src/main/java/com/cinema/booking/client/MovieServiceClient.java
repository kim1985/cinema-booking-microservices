package com.cinema.booking.client;

import com.cinema.shared.dto.ScreeningResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign Client per comunicazione con Movie Service
 * Service-to-Service communication per verifiche screening
 */
@FeignClient(
        name = "movie-service",
        configuration = MovieServiceClientConfig.class
)
public interface MovieServiceClient {

    /**
     * Ottieni dettagli screening per validazione prenotazione
     */
    @GetMapping("/api/movies/internal/screenings/{id}")
    ScreeningResponse getScreening(@PathVariable Long id);

    /**
     * Aggiorna posti disponibili dopo prenotazione
     * (chiamata interna, non tramite Gateway)
     */
    @PutMapping("/api/movies/internal/screenings/{id}/seats")
    void updateAvailableSeats(
            @PathVariable Long id,
            @RequestParam Integer seatsDelta
    );
}