package com.cinema.booking.controller;

import com.cinema.booking.service.BookingService;
import com.cinema.shared.dto.BookingRequest;
import com.cinema.shared.dto.BookingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller per prenotazioni cinema
 * Virtual Threads gestiscono automaticamente alta concorrenza
 */
@RestController
@RequestMapping("/api/bookings")
@Validated
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    /**
     * Creazione prenotazione sincrona
     * Virtual Thread automatico per ogni richiesta
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request) {

        log.info("New booking request: {} seats for screening {} (Virtual Thread: {})",
                request.numberOfSeats(), request.screeningId(), Thread.currentThread().isVirtual());

        BookingResponse booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    /**
     * Creazione prenotazione asincrona per test alta concorrenza
     * Dimostra Virtual Threads con CompletableFuture
     */
    @PostMapping("/async")
    public CompletableFuture<ResponseEntity<BookingResponse>> createBookingAsync(
            @Valid @RequestBody BookingRequest request) {

        log.info("Async booking request: {} seats for screening {}",
                request.numberOfSeats(), request.screeningId());

        return bookingService.createBookingAsync(request)
                .thenApply(booking -> ResponseEntity.status(HttpStatus.CREATED).body(booking));
    }

    /**
     * Recupera prenotazione per ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        return bookingService.getBooking(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista prenotazioni utente
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getUserBookings(
            @RequestParam String userEmail) {

        List<BookingResponse> bookings = bookingService.getUserBookings(userEmail);
        return ResponseEntity.ok(bookings);
    }

    /**
     * Cancella prenotazione
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestParam String userEmail) {

        log.info("Cancellation request for booking {} by {}", id, userEmail);

        BookingResponse cancelled = bookingService.cancelBooking(id, userEmail);
        return ResponseEntity.ok(cancelled);
    }

    /**
     * Health check specifico per Booking Service
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Booking Service is running with Virtual Threads");
    }

    /**
     * Test endpoint per verificare Virtual Threads
     */
    @GetMapping("/thread-info")
    public ResponseEntity<String> threadInfo() {
        Thread current = Thread.currentThread();
        String info = String.format("""
            Thread Info:
            - Virtual Thread: %s
            - Thread Name: %s  
            - Thread Class: %s
            """,
                current.isVirtual(),
                current.getName(),
                current.getClass().getSimpleName()
        );

        return ResponseEntity.ok(info);
    }
}
