package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * ============================================================================
 * TICKET-ADV072 — JwtTokenProvider (jjwt 0.12.x API)
 *
 * WHAT:    Generates + validates HS256-signed JWTs.
 * HOW:     Subject = email. Role goes into a custom "role" claim that
 *          {@link JwtAuthenticationFilter} turns into a GrantedAuthority.
 * WHY:     Self-contained (no DB hit per request) and stateless (no session).
 * OBSERVE: Decode any token at jwt.io with the configured secret.
 * ============================================================================
 *
 *  TODO(TICKET-ADV072):
 *    public String generate(String email, String role) {
 *        Instant now = Instant.now();
 *        Instant exp = now.plusSeconds(expirationMinutes * 60);
 *        return Jwts.builder()
 *            .subject(email)
 *            .issuer(issuer)
 *            .issuedAt(Date.from(now))
 *            .expiration(Date.from(exp))
 *            .claims(Map.of("role", role))
 *            .signWith(key)
 *            .compact();
 *    }
 *
 *    public Claims parse(String token) {
 *        return Jwts.parser()
 *            .verifyWith(key)
 *            .requireIssuer(issuer)
 *            .build()
 *            .parseSignedClaims(token)
 *            .getPayload();
 *    }
 *
 *  HINT: jjwt 0.12 uses .subject() / .issuer() / .claims() / .signWith() —
 *        the older 0.11 builder API (.setSubject etc.) is deprecated.
 *  GOTCHA: HS256 needs a key of at least 256 bits — short secrets throw
 *          io.jsonwebtoken.security.WeakKeyException at startup.
 * ============================================================================
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMinutes;
    private final String issuer;

    public JwtTokenProvider(@Value("${reconx.security.jwt.secret}") String secret,
                            @Value("${reconx.security.jwt.expiration-minutes}") long expirationMinutes,
                            @Value("${reconx.security.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
        this.issuer = issuer;
    }

    public String generate(String email, String role) {
        throw new UnsupportedOperationException("TICKET-ADV072");
    }

    public Claims parse(String token) {
        throw new UnsupportedOperationException("TICKET-ADV072");
    }

    public long expirationSeconds() {
        throw new UnsupportedOperationException("TICKET-ADV072");
    }
}
