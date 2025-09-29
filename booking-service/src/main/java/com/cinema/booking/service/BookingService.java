// File: booking-service/src/main/java/com/cinema/booking/service/BookingService.java
package com.cinema.booking.service;

import com.cinema.booking.client.MovieServiceClient;
import com.cinema.booking.domain.BookingDomainService;
import com.cinema.booking.domain.DistributedLockManager;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.exception.BookingException;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.shared.dto.BookingRequest;
import com.cinema.shared.dto.BookingResponse;
import com.cinema.shared.dto.ScreeningResponse;
import com.cinema.shared.enums.BookingStatus;
import com.cinema.shared.events.BookingCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Booking Service - Core mission-critical con Virtual Threads
 * Gestisce alta concorrenza per prenotazioni simultanee
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final MovieServiceClient movieServiceClient;
    private final DistributedLockManager lockManager;
    private final BookingDomainService bookingDomainService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creazione prenotazione asincrona con Virtual Threads
     */
    @Async("virtualThreadExecutor")
    public CompletableFuture<BookingResponse> createBookingAsync(BookingRequest request) {
        log.info("Processing async booking request for screening {} (Virtual Thread: {})",
                request.screeningId(), Thread.currentThread().isVirtual());

        try {
            BookingResponse response = createBooking(request);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("Async booking failed for screening {}: {}", request.screeningId(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Creazione prenotazione sincrona con Distributed Locking
     */
    public BookingResponse createBooking(BookingRequest request) {
        log.info("Creating booking: {} seats for screening {} by {}",
                request.numberOfSeats(), request.screeningId(), request.userEmail());

        try {
            // Validazione preliminare
            validateRequest(request);

            // Distributed lock per prevenire race conditions
            return lockManager.executeWithLock(
                    request.screeningId(),
                    () -> processBookingWithLock(request)
            );
        } catch (BookingException e) {
            // Errori di business - questi vanno propagati
            log.warn("Booking business rule failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Errori tecnici - log dettagliato ma messaggio user-friendly
            log.error("Technical error during booking creation for screening {}: {}",
                    request.screeningId(), e.getMessage(), e);

            // Ultimo tentativo senza lock per garantire successo
            try {
                log.warn("Attempting booking without distributed lock as fallback");
                return processBookingWithLock(request);
            } catch (Exception fallbackError) {
                log.error("Even fallback failed for screening {}: {}",
                        request.screeningId(), fallbackError.getMessage());
                throw new BookingException("Sistema temporaneamente sovraccarico, riprova tra 30 secondi");
            }
        }
    }

    /**
     * Validazione preliminare della richiesta
     */
    private void validateRequest(BookingRequest request) {
        if (request.screeningId() == null || request.screeningId() <= 0) {
            throw new BookingException("ID proiezione non valido");
        }

        if (request.userEmail() == null || request.userEmail().trim().isEmpty()) {
            throw new BookingException("Email utente obbligatoria");
        }

        if (request.numberOfSeats() == null || request.numberOfSeats() <= 0) {
            throw new BookingException("Numero posti deve essere positivo");
        }

        if (request.numberOfSeats() > 10) {
            throw new BookingException("Massimo 10 posti per prenotazione");
        }
    }

    /**
     * Elaborazione protetta da distributed lock
     */
    private BookingResponse processBookingWithLock(BookingRequest request) {
        ScreeningResponse screening = null;
        Booking booking = null;

        try {
            // 1. Ottieni info screening da Movie Service con fallback
            screening = getScreeningWithRetry(request.screeningId(), 3);

            // 2. Validazione disponibilità
            validateAvailability(screening, request);

            // 3. Crea booking entity
            booking = createBookingEntity(request, screening);

            // 4. Salva nel database
            Booking savedBooking = saveBookingWithRetry(booking, 3);

            // 5. Conferma booking usando Domain Service
            bookingDomainService.confirmBooking(savedBooking);
            savedBooking = bookingRepository.save(savedBooking);

            // 6. Aggiorna posti nel Movie Service (best effort)
            updateSeatsNonCritical(screening.id(), -request.numberOfSeats());

            // 7. Pubblica evento per notification
            publishBookingCreatedEvent(savedBooking, screening);

            log.info("Booking created successfully: ID {}", savedBooking.getId());
            return mapToResponse(savedBooking);

        } catch (BookingException e) {
            // Business exceptions vanno propagate
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in ultra safe processing: {}", e.getMessage(), e);

            // Se abbiamo già salvato il booking, restituiamolo
            if (booking != null && booking.getId() != null) {
                log.warn("Returning partially processed booking {}", booking.getId());
                return mapToResponse(booking);
            }

            throw new BookingException("Errore interno durante elaborazione prenotazione");
        }
    }

    /**
     * Ottieni screening con tentativi multipli automatici
     */
    private ScreeningResponse getScreeningWithRetry(Long screeningId, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ScreeningResponse screening = movieServiceClient.getScreening(screeningId);
                if (screening == null) {
                    throw new BookingException("Proiezione non trovata: " + screeningId);
                }
                return screening;
            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to get screening {} on attempt {}: {}",
                        screeningId, attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(50 * attempt); // Attesa crescente: 50, 100, 150ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BookingException("Operazione interrotta");
                    }
                }
            }
        }

        log.error("Failed to get screening {} after {} attempts", screeningId, maxRetries);
        throw new BookingException("Proiezione temporaneamente non disponibile");
    }

    /**
     * Validazione disponibilità con pattern matching semplificato
     */
    private void validateAvailability(ScreeningResponse screening, BookingRequest request) {
        try {
            Integer availableSeats = screening.availableSeats();

            // Gestione sicurezza per valori null
            if (availableSeats == null) {
                log.warn("Null available seats for screening {}, assuming 0", screening.id());
                availableSeats = 0;
            }

            if (availableSeats <= 0) {
                throw new BookingException("Sold out! Nessun posto disponibile");
            }

            if (availableSeats < request.numberOfSeats()) {
                throw new BookingException("Solo " + availableSeats + " posti disponibili");
            }

            // Validazione timing sicura
            if (screening.startTime() != null) {
                LocalDateTime now = LocalDateTime.now();
                if (screening.startTime().isBefore(now)) {
                    throw new BookingException("Proiezione già iniziata");
                }

                if (screening.startTime().minusMinutes(30).isBefore(now)) {
                    throw new BookingException("Prenotazioni chiuse 30 minuti prima dell'inizio");
                }
            }

            log.debug("Validation passed: {} seats available, {} requested", availableSeats, request.numberOfSeats());

        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected validation error: {}", e.getMessage(), e);
            throw new BookingException("Errore validazione disponibilità");
        }
    }

    private Booking createBookingEntity(BookingRequest request, ScreeningResponse screening) {
        try {
            Booking booking = new Booking();
            booking.setScreeningId(request.screeningId());
            booking.setUserEmail(request.userEmail().trim().toLowerCase());
            booking.setNumberOfSeats(request.numberOfSeats());

            // Calcolo prezzo
            BigDecimal price = screening.price() != null ? screening.price() : BigDecimal.valueOf(10.00);
            booking.setTotalPrice(price.multiply(BigDecimal.valueOf(request.numberOfSeats())));

            booking.setStatus(BookingStatus.PENDING);
            booking.setMovieTitle(screening.movieTitle() != null ? screening.movieTitle() : "Film");
            booking.setScreeningTime(screening.startTime());
            booking.setCreatedAt(LocalDateTime.now());

            return booking;
        } catch (Exception e) {
            log.error("Error creating booking entity: {}", e.getMessage(), e);
            throw new BookingException("Errore creazione prenotazione");
        }
    }

    /**
     * Salvataggio con tentativi multipli per gestire carico del database
     */
    private Booking saveBookingWithRetry(Booking booking, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return bookingRepository.save(booking);
            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to save booking on attempt {}: {}", attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(30 * attempt); // Attesa crescente: 30, 60, 90ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BookingException("Operazione interrotta");
                    }
                }
            }
        }

        log.error("Failed to save booking after {} attempts", maxRetries);
        throw new BookingException("Errore salvataggio prenotazione");
    }

    /**
     * Aggiorna posti con gestione errori graceful
     */
    private void updateSeatsNonCritical(Long screeningId, Integer seatsDelta) {
        try {
            movieServiceClient.updateAvailableSeats(screeningId, seatsDelta);
        } catch (Exception e) {
            log.warn("Failed to update seats for screening {}: {}", screeningId, e.getMessage());
            // Non interrompiamo il processo - il booking è già salvato
        }
    }

    private void publishBookingCreatedEvent(Booking booking, ScreeningResponse screening) {
        try {
            BookingCreatedEvent event = new BookingCreatedEvent(
                    booking.getId(),
                    booking.getUserEmail(),
                    screening.movieTitle(),
                    screening.startTime(),
                    booking.getNumberOfSeats(),
                    booking.getTotalPrice()
            );
            eventPublisher.publishEvent(event);
            log.debug("Published booking created event for booking {}", booking.getId());
        } catch (Exception e) {
            log.warn("Failed to publish booking event: {}", e.getMessage());
            // Non interrompiamo il processo per un errore di notification
        }
    }

    // Metodi di lettura (senza lock)

    @Transactional(readOnly = true)
    public Optional<BookingResponse> getBooking(Long id) {
        try {
            return bookingRepository.findById(id).map(this::mapToResponse);
        } catch (Exception e) {
            log.error("Error retrieving booking {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userEmail) {
        try {
            return bookingRepository.findByUserEmailOrderByCreatedAtDesc(userEmail.trim().toLowerCase())
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving bookings for user {}: {}", userEmail, e.getMessage());
            return List.of();
        }
    }

    /**
     * Cancellazione booking con business rules - FIXED
     */
    public BookingResponse cancelBooking(Long bookingId, String userEmail) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BookingException("Prenotazione non trovata"));

            // Verifica autorizzazione
            if (!booking.getUserEmail().equalsIgnoreCase(userEmail.trim())) {
                throw new BookingException("Non autorizzato a cancellare questa prenotazione");
            }

            // Usa Domain Service per business logic
            if (!bookingDomainService.canBeCancelled(booking)) {
                throw new BookingException("Prenotazione non può essere cancellata");
            }

            bookingDomainService.cancelBooking(booking);

            // Rilascia i posti (best effort)
            updateSeatsNonCritical(booking.getScreeningId(), booking.getNumberOfSeats());

            Booking cancelled = bookingRepository.save(booking);
            log.info("Booking {} cancelled by {}", bookingId, userEmail);

            return mapToResponse(cancelled);

        } catch (BookingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error cancelling booking {}: {}", bookingId, e.getMessage());
            throw new BookingException("Errore durante cancellazione prenotazione");
        }
    }

    private BookingResponse mapToResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getScreeningId(),
                booking.getUserEmail(),
                booking.getNumberOfSeats(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getMovieTitle(),
                booking.getScreeningTime()
        );
    }
}