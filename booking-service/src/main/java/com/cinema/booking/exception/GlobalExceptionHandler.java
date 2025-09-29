package com.cinema.booking.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestore globale degli errori per garantire risposte consistenti
 * Cattura tutti gli errori e fornisce messaggi comprensibili agli utenti
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ErrorResponse> handleBookingException(BookingException e) {
        log.warn("Booking exception: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(
                "BOOKING_ERROR",
                e.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Validation exception: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String message = "Dati non validi: " + errors.entrySet().iterator().next().getValue();

        ErrorResponse error = new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                LocalDateTime.now()
        );

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(
                "INVALID_ARGUMENT",
                "Parametro non valido: " + e.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(Exception e) {
        log.error("Database error: {}", e.getMessage(), e);

        ErrorResponse error = new ErrorResponse(
                "DATABASE_ERROR",
                "Errore temporaneo del database, riprova tra poco",
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleResourceAccessException(Exception e) {
        log.error("External service error: {}", e.getMessage(), e);

        ErrorResponse error = new ErrorResponse(
                "SERVICE_UNAVAILABLE",
                "Servizio temporaneamente non disponibile",
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(feign.FeignException e) {
        log.error("Feign client error: {} - {}", e.status(), e.getMessage());

        String message = switch (e.status()) {
            case 404 -> "Risorsa non trovata";
            case 500 -> "Errore interno del servizio esterno";
            case 503 -> "Servizio temporaneamente non disponibile";
            default -> "Errore di comunicazione con servizio esterno";
        };

        ErrorResponse error = new ErrorResponse(
                "EXTERNAL_SERVICE_ERROR",
                message,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);

        ErrorResponse error = new ErrorResponse(
                "RUNTIME_ERROR",
                "Errore temporaneo del sistema, riprova tra poco",
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);

        ErrorResponse error = new ErrorResponse(
                "SYSTEM_ERROR",
                "Errore imprevisto del sistema",
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Risposta standard per errori
     */
    public record ErrorResponse(
            String errorCode,
            String message,
            LocalDateTime timestamp
    ) {}
}
