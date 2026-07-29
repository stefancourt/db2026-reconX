package com.dbtraining.reconx.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * TICKET-ADV062 — RFC 7807 ProblemDetail for every ReconException
 *
 * Maps each domain exception subtype to the right HTTP status, with a
 * structured ProblemDetail body so clients don't have to parse free text.
 * ============================================================================
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TradeNotFoundException.class)
    public ProblemDetail notFound(TradeNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/trade-not-found"));
        pd.setTitle("Trade not found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DuplicateTradeRefException.class)
    public ProblemDetail duplicate(DuplicateTradeRefException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/duplicate-trade-ref"));
        pd.setTitle("Duplicate trade reference");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidTradeException.class)
    public ProblemDetail invalid(InvalidTradeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/invalid-trade"));
        pd.setTitle("Invalid trade");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ReconciliationMismatchException.class)
    public ProblemDetail mismatch(ReconciliationMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/recon-failure"));
        pd.setTitle("Reconciliation failure");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/validation-failed"));
        pd.setTitle("Validation failed");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraint(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/constraint-violation"));
        pd.setTitle("Validation failed");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail fallback(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred — please contact support with the correlationId");
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/internal-server-error"));
        pd.setTitle("Internal server error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
