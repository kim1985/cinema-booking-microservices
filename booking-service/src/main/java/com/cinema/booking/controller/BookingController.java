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

        log.info("New booking request: {} seats for screening {} by {} (Virtual Thread: {})",
                request.numberOfSeats(), request.screeningId(), request.userEmail(),
                Thread.currentThread().isVirtual());

        try {
            BookingResponse booking = bookingService.createBooking(request);
            log.info("Booking created successfully: ID {}", booking.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (Exception e) {
            log.error("Failed to create booking for screening {}: {}", request.screeningId(), e.getMessage());
            throw e; // Lascia gestire al GlobalExceptionHandler
        }
    }

    /**
     * Creazione prenotazione asincrona per test alta concorrenza
     * Dimostra Virtual Threads con CompletableFuture
     */
    @PostMapping("/async")
    public CompletableFuture<ResponseEntity<BookingResponse>> createBookingAsync(
            @Valid @RequestBody BookingRequest request) {

        log.info("Async booking request: {} seats for screening {} (Virtual Thread: {})",
                request.numberOfSeats(), request.screeningId(), Thread.currentThread().isVirtual());

        return bookingService.createBookingAsync(request)
                .thenApply(booking -> {
                    log.info("Async booking completed: ID {}", booking.id());
                    return ResponseEntity.status(HttpStatus.CREATED).body(booking);
                })
                .exceptionally(throwable -> {
                    log.error("Async booking failed: {}", throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                });
    }

    /**
     * Recupera prenotazione per ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        try {
            return bookingService.getBooking(id)
                    .map(booking -> {
                        log.debug("Retrieved booking: {}", id);
                        return ResponseEntity.ok(booking);
                    })
                    .orElseGet(() -> {
                        log.warn("Booking not found: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            log.error("Error retrieving booking {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * Lista prenotazioni utente
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getUserBookings(
            @RequestParam String userEmail) {

        try {
            if (userEmail == null || userEmail.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            List<BookingResponse> bookings = bookingService.getUserBookings(userEmail);
            log.debug("Retrieved {} bookings for user {}", bookings.size(), userEmail);
            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            log.error("Error retrieving bookings for user {}: {}", userEmail, e.getMessage());
            throw e;
        }
    }

    /**
     * Cancella prenotazione - FIXED
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestParam String userEmail) {

        try {
            if (userEmail == null || userEmail.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            log.info("Cancellation request for booking {} by {}", id, userEmail);

            BookingResponse cancelled = bookingService.cancelBooking(id, userEmail);
            log.info("Booking {} cancelled successfully", id);

            return ResponseEntity.ok(cancelled);
        } catch (Exception e) {
            log.error("Failed to cancel booking {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * Health check specifico per Booking Service
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            // Test database connection
            bookingService.getUserBookings("health@test.com");

            HealthResponse health = new HealthResponse(
                    "UP",
                    "Booking Service is running with Virtual Threads",
                    Thread.currentThread().isVirtual(),
                    System.currentTimeMillis()
            );

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            HealthResponse health = new HealthResponse(
                    "DOWN",
                    "Service experiencing issues: " + e.getMessage(),
                    Thread.currentThread().isVirtual(),
                    System.currentTimeMillis()
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    /**
     * Test endpoint per verificare Virtual Threads
     */
    @GetMapping("/thread-info")
    public ResponseEntity<ThreadInfoResponse> threadInfo() {
        Thread current = Thread.currentThread();

        ThreadInfoResponse info = new ThreadInfoResponse(
                current.isVirtual(),
                current.getName(),
                current.getClass().getSimpleName(),
                System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors()
        );

        return ResponseEntity.ok(info);
    }

    /**
     * Stress test endpoint per verificare gestione concorrenza
     */
    @GetMapping("/stress-test")
    public ResponseEntity<StressTestResponse> stressTest() {
        try {
            long startTime = System.currentTimeMillis();

            // Simula operazione pesante
            Thread.sleep(100);

            long endTime = System.currentTimeMillis();

            StressTestResponse response = new StressTestResponse(
                    "SUCCESS",
                    endTime - startTime,
                    Thread.currentThread().isVirtual(),
                    Thread.currentThread().getName()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Stress test failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Response DTOs
    public record HealthResponse(
            String status,
            String message,
            boolean virtualThread,
            long timestamp
    ) {}

    public record ThreadInfoResponse(
            boolean isVirtual,
            String threadName,
            String threadClass,
            String javaVersion,
            int availableProcessors
    ) {}

    public record StressTestResponse(
            String status,
            long responseTimeMs,
            boolean virtualThread,
            String threadName
    ) {}
}
