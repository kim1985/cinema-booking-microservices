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

        return CompletableFuture.completedFuture(createBooking(request));
    }

    /**
     * Creazione prenotazione sincrona con Distributed Locking
     */
    public BookingResponse createBooking(BookingRequest request) {
        log.info("Creating booking: {} seats for screening {} by {}",
                request.numberOfSeats(), request.screeningId(), request.userEmail());

        // Distributed lock per prevenire race conditions
        return lockManager.executeWithLock(
                request.screeningId(),
                () -> processBookingWithLock(request)
        );
    }

    /**
     * Elaborazione protetta da distributed lock
     */
    private BookingResponse processBookingWithLock(BookingRequest request) {
        // 1. Ottieni info screening da Movie Service
        ScreeningResponse screening = movieServiceClient.getScreening(request.screeningId());

        // 2. Validazione
        validateBookingRequest(screening, request);

        // 3. Crea booking entity
        var booking = createBookingEntity(request, screening);

        // 4. Salva nel database
        var savedBooking = bookingRepository.save(booking);

        // 5. Conferma booking usando Domain Service
        bookingDomainService.confirmBooking(savedBooking);
        bookingRepository.save(savedBooking);

        // 6. Aggiorna posti nel Movie Service
        movieServiceClient.updateAvailableSeats(screening.id(), -request.numberOfSeats());

        // 7. Pubblica evento per notification
        publishBookingCreatedEvent(savedBooking, screening);

        log.info("Booking created successfully: ID {}", savedBooking.getId());
        return mapToResponse(savedBooking);
    }

    /**
     * Validazione senza pattern matching con primitives
     */
    private void validateBookingRequest(ScreeningResponse screening, BookingRequest request) {
        // Validazione disponibilità posti
        Integer availableSeats = screening.availableSeats();

        if (availableSeats == null) {
            throw new BookingException("Screening non valido");
        }

        if (availableSeats <= 0) {
            throw new BookingException("Sold out! Nessun posto disponibile");
        }

        if (availableSeats < request.numberOfSeats()) {
            throw new BookingException("Solo %d posti disponibili".formatted(availableSeats));
        }

        // Validazione timing
        var now = LocalDateTime.now();

        if (screening.startTime().isBefore(now)) {
            throw new BookingException("Proiezione già iniziata");
        }

        if (screening.startTime().minusMinutes(30).isBefore(now)) {
            throw new BookingException("Prenotazioni chiuse 30 minuti prima dell'inizio");
        }

        log.debug("Validation passed: {} seats available, {} requested", availableSeats, request.numberOfSeats());
    }

    private Booking createBookingEntity(BookingRequest request, ScreeningResponse screening) {
        var booking = new Booking();
        booking.setScreeningId(request.screeningId());
        booking.setUserEmail(request.userEmail());
        booking.setNumberOfSeats(request.numberOfSeats());
        booking.setTotalPrice(screening.price().multiply(BigDecimal.valueOf(request.numberOfSeats())));
        booking.setStatus(BookingStatus.PENDING);
        booking.setMovieTitle(screening.movieTitle());
        booking.setScreeningTime(screening.startTime());
        booking.setCreatedAt(LocalDateTime.now());
        return booking;
    }

    private void publishBookingCreatedEvent(Booking booking, ScreeningResponse screening) {
        var event = new BookingCreatedEvent(
                booking.getId(),
                booking.getUserEmail(),
                screening.movieTitle(),
                screening.startTime(),
                booking.getNumberOfSeats(),
                booking.getTotalPrice()
        );
        eventPublisher.publishEvent(event);
        log.debug("Published booking created event for booking {}", booking.getId());
    }

    // Metodi di lettura (senza lock)

    @Transactional(readOnly = true)
    public Optional<BookingResponse> getBooking(Long id) {
        return bookingRepository.findById(id).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userEmail) {
        return bookingRepository.findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Cancellazione booking con business rules
     */
    public BookingResponse cancelBooking(Long bookingId, String userEmail) {
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException("Prenotazione non trovata"));

        // Verifica autorizzazione
        if (!booking.getUserEmail().equals(userEmail)) {
            throw new BookingException("Non autorizzato");
        }

        // Usa Domain Service per business logic
        if (!bookingDomainService.canBeCancelled(booking)) {
            throw new BookingException("Prenotazione non cancellabile");
        }

        bookingDomainService.cancelBooking(booking);

        // Rilascia i posti
        movieServiceClient.updateAvailableSeats(booking.getScreeningId(), booking.getNumberOfSeats());

        var cancelled = bookingRepository.save(booking);
        log.info("Booking {} cancelled by {}", bookingId, userEmail);

        return mapToResponse(cancelled);
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