package com.dbtraining.reconx.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ============================================================================
 * TICKET-ADV073 — JwtAuthenticationFilter
 *
 * Drives the filter directly with Spring's servlet mocks: a real
 * JwtTokenProvider mints the tokens, so signature, issuer and expiry checks
 * are the production ones. The three things the ticket cares about are
 * asserted here — the context gets populated on a good token, an unusable
 * token leaves the request anonymous WITHOUT throwing, and the filter body
 * runs at most once per request.
 * ============================================================================
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";
    private static final String ISSUER = "reconx";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, ISSUER);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenPopulatesTheSecurityContext() throws Exception {
        MockFilterChain chain = doFilter(bearer(provider.generate("admin@db.com", "ADMIN")));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("admin@db.com");
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(auth.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
        // the request continued down the chain
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void roleClaimIsPrefixedOnceSoHasRoleMatches() throws Exception {
        doFilter(bearer(provider.generate("recon@db.com", "RECON_ANALYST")));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_RECON_ANALYST");  // not ROLE_ROLE_...
    }

    @Test
    void requestWithoutAnAuthorizationHeaderStaysAnonymousAndContinues() throws Exception {
        MockFilterChain chain = doFilter(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        MockFilterChain chain = doFilter("Basic YWRtaW5AZGIuY29tOmFkbWluMTIz");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void garbageTokenFailsClosedWithoutThrowing() throws Exception {
        MockFilterChain chain = doFilter("Bearer not.a.real.token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();   // no exception, chain still ran
    }

    @Test
    void emptyBearerValueFailsClosedWithoutThrowing() throws Exception {
        // jjwt throws IllegalArgumentException — not JwtException — for an empty token.
        MockFilterChain chain = doFilter("Bearer ");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void expiredTokenFailsClosed() throws Exception {
        String expired = new JwtTokenProvider(SECRET, -1, ISSUER).generate("admin@db.com", "ADMIN");

        doFilter(bearer(expired));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenSignedWithAnotherSecretFailsClosed() throws Exception {
        String foreign = new JwtTokenProvider("a-completely-different-secret-32-bytes!!", 60, ISSUER)
                .generate("admin@db.com", "ADMIN");

        doFilter(bearer(foreign));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void signedTokenWithoutARoleClaimFailsClosed() throws Exception {
        // Signature is valid, but there is no role to authorise against.
        String noRole = io.jsonwebtoken.Jwts.builder()
                .subject("admin@db.com")
                .issuer(ISSUER)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        doFilter(bearer(noRole));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void badTokenClearsAnAlreadyPopulatedContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stale@db.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        doFilter("Bearer not.a.real.token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void filterBodyRunsOnlyOncePerRequest() throws Exception {
        JwtTokenProvider spyProvider = spy(new JwtTokenProvider(SECRET, 60, ISSUER));
        JwtAuthenticationFilter onceFilter = new JwtAuthenticationFilter(spyProvider);
        String token = provider.generate("trader@db.com", "TRADER");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/trades");
        req.addHeader("Authorization", bearer(token));
        MockHttpServletResponse res = new MockHttpServletResponse();

        // Re-entry within one request (what a nested dispatch looks like): the chain
        // hands the same request back to the same filter. OncePerRequestFilter marks
        // the request on the first pass, so the body must not run a second time.
        FilterChain reentrant = (r, s) -> onceFilter.doFilter(r, s, new MockFilterChain());
        onceFilter.doFilter(req, res, reentrant);

        verify(spyProvider, times(1)).parse(token);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("trader@db.com");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private MockFilterChain doFilter(String authorizationHeader) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/trades");
        if (authorizationHeader != null) {
            req.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return chain;
    }
}
