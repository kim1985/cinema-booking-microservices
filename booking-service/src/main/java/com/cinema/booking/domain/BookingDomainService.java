package com.cinema.booking.domain;

import com.cinema.booking.entity.Booking;
import com.cinema.shared.enums.BookingStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BookingDomainService {

    public void confirmBooking(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setConfirmedAt(LocalDateTime.now());
        }
    }

    public boolean canBeCancelled(Booking booking) {
        BookingStatus status = booking.getStatus();

        if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED) {
            return false;
        }

        if (status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED) {
            return isNotTooLateToCancel(booking);
        }

        return false;
    }

    public void cancelBooking(Booking booking) {
        if (!canBeCancelled(booking)) {
            throw new IllegalStateException("Booking cannot be cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
    }

    public String getStatusMessage(Booking booking) {
        return switch (booking.getStatus()) {
            case PENDING -> "Prenotazione in elaborazione per %s".formatted(booking.getMovieTitle());
            case CONFIRMED -> "Confermato! %d posti per %s".formatted(
                    booking.getNumberOfSeats(),
                    booking.getMovieTitle()
            );
            case CANCELLED -> "Prenotazione cancellata per %s".formatted(booking.getMovieTitle());
            case EXPIRED -> "Prenotazione scaduta per %s".formatted(booking.getMovieTitle());
        };
    }

    private boolean isNotTooLateToCancel(Booking booking) {
        if (booking.getScreeningTime() == null) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = booking.getScreeningTime().minusHours(2);

        return now.isBefore(cutoffTime);
    }
}