package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.LoginRequest;
import com.dbtraining.reconx.dto.LoginResponse;
import com.dbtraining.reconx.exception.InvalidTradeException;
import com.dbtraining.reconx.repository.AppUserRepository;
import com.dbtraining.reconx.repository.entity.AppUser;
import com.dbtraining.reconx.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * TICKET-ADV072 — POST /api/auth/login
 *
 * Verifies BCrypt password, returns a JWT carrying the user's role.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtTokenProvider jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        // TODO(TICKET-ADV072): look up the user by email, verify BCrypt password,
        //   then call jwt.generate(email, role) and return a LoginResponse.
        //   Reject with InvalidTradeException("Invalid credentials") on any mismatch
        //   (do NOT leak whether the email or the password was the problem).
        throw new UnsupportedOperationException("TICKET-ADV072");
    }
}
