package com.dbtraining.reconx.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * ============================================================================
 * TICKET-ADV072 — 401 for failed authentication
 *
 * WHAT:    Maps Spring Security's AuthenticationException (thrown by
 *          AuthController on a bad email or password) to HTTP 401.
 * HOW:     Lives in the `api` module because `common` — where
 *          {@link GlobalExceptionHandler} sits — has no Spring Security on
 *          its classpath. Ordered first so it wins over that advice's
 *          Exception.class fallback.
 * WHY:     A wrong password is not a 400 "invalid payload"; clients (and the
 *          React login form) branch on 401 to re-prompt for credentials.
 * OBSERVE: POST /api/auth/login with a wrong password returns 401 with an
 *          RFC 7807 body whose detail never says which half was wrong.
 * ============================================================================
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthorized(AuthenticationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/invalid-credentials"));
        pd.setTitle("Authentication failed");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
