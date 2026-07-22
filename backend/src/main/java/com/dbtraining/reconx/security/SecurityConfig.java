package com.dbtraining.reconx.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ============================================================================
 * SecurityConfig — TICKET-ADV073 + TICKET-ADV074
 * ============================================================================
 * WHAT:    Spring Security filter chain. Production target: stateless JWT
 *          auth + method-level RBAC across ADMIN / TRADER / VIEWER /
 *          RECON_ANALYST roles.
 * HOW:     One SecurityFilterChain @Bean + PasswordEncoder @Bean +
 *          @EnableMethodSecurity. The JwtAuthenticationFilter is registered
 *          before UsernamePasswordAuthenticationFilter.
 * WHY:     Day 6 needs role-based protection on every endpoint, and the
 *          frontend uses bearer tokens issued at /auth/login.
 * OBSERVE: After Day-6 work is wired, GET /api/v1/trades without a token -> 401.
 * ============================================================================
 *
 *  DAY-1 DEFAULT (below): everything is `permitAll`. This lets the frontend
 *  and Swagger UI load on Day 1 without an auth UI. TICKET-ADV073 + ADV074
 *  replace this with proper JWT + role-based auth.
 *
 *  TODO(TICKET-ADV073 + ADV074):
 *    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 *
 *    @Bean
 *    public SecurityFilterChain filterChain(HttpSecurity http,
 *                                           JwtAuthenticationFilter jwtFilter) throws Exception {
 *        http
 *          .csrf(AbstractHttpConfigurer::disable)
 *          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 *          .authorizeHttpRequests(auth -> auth
 *            .requestMatchers("/auth/login","/actuator/health/**","/actuator/info",
 *                             "/actuator/prometheus","/swagger-ui.html","/swagger-ui/**",
 *                             "/v3/api-docs/**","/h2/**").permitAll()
 *            .requestMatchers(HttpMethod.GET,    "/v1/trades/**").hasAnyRole("VIEWER","TRADER","RECON_ANALYST","ADMIN")
 *            .requestMatchers(HttpMethod.POST,   "/v1/trades").hasAnyRole("TRADER","ADMIN")
 *            .requestMatchers(HttpMethod.PUT,    "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
 *            .requestMatchers(HttpMethod.PATCH,  "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
 *            .requestMatchers(HttpMethod.DELETE, "/v1/trades/**").hasRole("ADMIN")
 *            .requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST","ADMIN")
 *            .requestMatchers("/v1/audit/**").hasAnyRole("RECON_ANALYST","ADMIN")
 *            .anyRequest().authenticated())
 *          .headers(h -> h.frameOptions(f -> f.disable()))
 *          .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
 *        return http.build();
 *    }
 *
 *  HINT: Also add @EnableMethodSecurity on the class so @PreAuthorize on
 *        service methods is honoured.
 * ============================================================================
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // ====================================================================
        // Day-1 permissive default — replace with TICKET-ADV073 + ADV074 rules.
        // ====================================================================
        // TODO(TICKET-ADV073 + ADV074): swap this permitAll() block for the
        //   stateless JWT + role-based chain shown in the Javadoc above.
        // ====================================================================

        return http
                .csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.disable())) // allow /h2 in dev
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    // TODO(TICKET-ADV073): @Bean PasswordEncoder (BCrypt).
    // TODO(TICKET-ADV073): register JwtAuthenticationFilter before
    //                     UsernamePasswordAuthenticationFilter.
    // TODO(TICKET-ADV074): add @EnableMethodSecurity and the RBAC matchers.
}
