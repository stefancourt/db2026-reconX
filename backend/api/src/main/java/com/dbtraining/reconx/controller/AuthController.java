package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.LoginRequest;
import com.dbtraining.reconx.dto.LoginResponse;
import com.dbtraining.reconx.repository.AppUserRepository;
import com.dbtraining.reconx.domain.AppUser;
import com.dbtraining.reconx.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getEnabled())
                || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwt.generate(user.getEmail(), user.getRole());
        return ResponseEntity.ok(
                new LoginResponse(token, "Bearer", jwt.expirationSeconds(), user.getRole()));
    }
}
