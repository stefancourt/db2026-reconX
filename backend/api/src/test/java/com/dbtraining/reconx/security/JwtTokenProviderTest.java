package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * TICKET-ADV072 — JwtTokenProvider unit tests
 *
 * No Spring context and no database: the provider is a plain component whose
 * three constructor arguments are the whole configuration surface, so the test
 * builds it directly. That keeps the signing/verification contract verifiable
 * in milliseconds, independent of the Testcontainers-backed suite.
 * ============================================================================
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";
    private static final String ISSUER = "reconx";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, ISSUER);

    @Test
    void generatedTokenIsAThreePartHs256Jwt() {
        String token = provider.generate("admin@db.com", "ADMIN");

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void roundTripCarriesSubjectRoleIssuerAndTimestamps() {
        Instant before = Instant.now().minusSeconds(1);

        Claims claims = provider.parse(provider.generate("trader@db.com", "TRADER"));

        assertThat(claims.getSubject()).isEqualTo("trader@db.com");
        assertThat(claims.get("role")).isEqualTo("TRADER");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getIssuedAt().toInstant()).isAfterOrEqualTo(before);
        // 60-minute validity, allowing a second of clock drift on either side.
        assertThat(claims.getExpiration().toInstant())
                .isBetween(claims.getIssuedAt().toInstant().plusSeconds(3599),
                           claims.getIssuedAt().toInstant().plusSeconds(3601));
    }

    @Test
    void expirationSecondsMatchesConfiguredMinutes() {
        assertThat(provider.expirationSeconds()).isEqualTo(3600);
        assertThat(new JwtTokenProvider(SECRET, 15, ISSUER).expirationSeconds()).isEqualTo(900);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtTokenProvider("a-completely-different-secret-32-bytes!!", 60, ISSUER)
                .generate("admin@db.com", "ADMIN");

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = new JwtTokenProvider(SECRET, 60, "some-other-service")
                .generate("admin@db.com", "ADMIN");

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = provider.generate("viewer@db.com", "VIEWER").split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"viewer@db.com\",\"iss\":\"" + ISSUER + "\",\"role\":\"ADMIN\"}")
                        .getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> provider.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void alreadyExpiredTokenIsRejected() {
        // Negative validity puts `exp` in the past the moment the token is minted.
        String expired = new JwtTokenProvider(SECRET, -1, ISSUER).generate("admin@db.com", "ADMIN");

        assertThatThrownBy(() -> provider.parse(expired)).isInstanceOf(JwtException.class);
    }
}
