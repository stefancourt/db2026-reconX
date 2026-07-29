package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * ============================================================================
 * TICKET-ADV073 — JwtAuthenticationFilter
 *
 * WHAT:    Reads `Authorization: Bearer <token>`, parses it via
 *          {@link JwtTokenProvider}, and sets the SecurityContext for the
 *          current request.
 * HOW:     Extends OncePerRequestFilter so it runs exactly once per request.
 *          On a bad / expired token the context is cleared (NOT a 401) —
 *          Spring's normal auth path turns the missing principal into a 401
 *          when a protected endpoint is hit.
 * WHY:     Stateless auth: every request carries its own credential.
 * OBSERVE: A request with a valid token populates SecurityContextHolder; the
 *          downstream controller can use @AuthenticationPrincipal etc.
 * ============================================================================
 *
 *  TODO(TICKET-ADV073):
 *    String header = req.getHeader("Authorization");
 *    if (header != null && header.startsWith("Bearer ")) {
 *        String token = header.substring(7);
 *        try {
 *            Claims claims = provider.parse(token);
 *            String email = claims.getSubject();
 *            String role  = (String) claims.get("role");
 *            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
 *            var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
 *            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
 *            SecurityContextHolder.getContext().setAuthentication(auth);
 *        } catch (JwtException ex) {
 *            SecurityContextHolder.clearContext();
 *        }
 *    }
 *    chain.doFilter(req, res);
 *
 *  HINT: Always call chain.doFilter at the end — even on auth failure — so
 *        Spring's normal exception flow can produce a clean 401.
 * ============================================================================
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider provider;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = provider.parse(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                if (email == null || email.isBlank() || role == null || role.isBlank()) {
                    // A signed token missing either half is not usable as a principal.
                    throw new MalformedJwtException("Token is missing sub or role");
                }
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                // Fail closed, never throw: an unusable token leaves the request
                // anonymous and the chain decides the status code.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
