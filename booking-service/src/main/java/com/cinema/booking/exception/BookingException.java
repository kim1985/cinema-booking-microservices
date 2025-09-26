package com.cinema.booking.exception;

/**
 * Business exception per errori delle prenotazioni
 */
public class BookingException extends RuntimeException {

    public BookingException(String message) {
        super(message);
    }

}