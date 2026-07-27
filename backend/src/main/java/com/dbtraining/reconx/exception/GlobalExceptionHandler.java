package com.dbtraining.reconx.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(TradeNotFoundException.class)
    public ProblemDetail notFound(TradeNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateTradeRefException.class)
    public ProblemDetail duplicate(DuplicateTradeRefException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidTradeException.class)
    public ProblemDetail invalid(InvalidTradeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ReconciliationMismatchException.class)
    public ProblemDetail mismatch(ReconciliationMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraint(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
