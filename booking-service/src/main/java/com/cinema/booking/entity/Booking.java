package com.cinema.booking.entity;

import com.cinema.shared.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Booking Entity - Solo data holder
 * Business logic delegata ai Domain Services per SOLID principles
 */
@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Riferimento a screening nel Movie Service (no FK, microservizi separati)
    @Column(name = "screening_id", nullable = false)
    private Long screeningId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "number_of_seats", nullable = false)
    private Integer numberOfSeats;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // Metadati per business logic
    @Column(name = "movie_title", length = 200)
    private String movieTitle; // Denormalizzato per performance

    @Column(name = "screening_time")
    private LocalDateTime screeningTime; // Denormalizzato per business rules

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BookingStatus.PENDING;
        }
    }
}