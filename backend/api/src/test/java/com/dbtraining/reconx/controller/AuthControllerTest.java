package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.domain.AppUser;
import com.dbtraining.reconx.exception.AuthExceptionHandler;
import com.dbtraining.reconx.exception.GlobalExceptionHandler;
import com.dbtraining.reconx.repository.AppUserRepository;
import com.dbtraining.reconx.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * TICKET-ADV072 — POST /auth/login behaviour
 *
 * Standalone MockMvc: the controller plus the two @RestControllerAdvice
 * classes, a real BCrypt encoder and a real JwtTokenProvider, with only the
 * repository mocked. No Spring context, no Postgres, no Docker — the login
 * contract (200 envelope / 401 on bad credentials / 400 on a malformed body)
 * is fully determined by those collaborators.
 *
 * NOTE: paths here have no `/api` prefix — that comes from
 *       `server.servlet.context-path`, which a standalone MockMvc does not apply.
 * ============================================================================
 */
class AuthControllerTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";
    private static final String RAW_PASSWORD = "trader123";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwt = new JwtTokenProvider(SECRET, 60, "reconx");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(users, encoder, jwt))
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validCredentialsReturnASignedTokenEnvelope() throws Exception {
        when(users.findByEmail("trader@db.com"))
                .thenReturn(Optional.of(user("trader@db.com", RAW_PASSWORD, "TRADER", true)));

        MvcResult result = mvc.perform(login("trader@db.com", RAW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.role").value("TRADER"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        // and the token in the envelope really verifies against the configured secret
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Claims claims = jwt.parse(token);
        assertThat(claims.getSubject()).isEqualTo("trader@db.com");
        assertThat(claims.get("role")).isEqualTo("TRADER");
    }

    @Test
    void wrongPasswordReturns401AndNoToken() throws Exception {
        when(users.findByEmail("trader@db.com"))
                .thenReturn(Optional.of(user("trader@db.com", RAW_PASSWORD, "TRADER", true)));

        mvc.perform(login("trader@db.com", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void unknownEmailReturns401WithTheSameDetailAsAWrongPassword() throws Exception {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        mvc.perform(login("nobody@db.com", RAW_PASSWORD))
                .andExpect(status().isUnauthorized())
                // identical message on both paths — no user-enumeration oracle
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void disabledUserReturns401EvenWithTheRightPassword() throws Exception {
        when(users.findByEmail("viewer@db.com"))
                .thenReturn(Optional.of(user("viewer@db.com", RAW_PASSWORD, "VIEWER", false)));

        mvc.perform(login("viewer@db.com", RAW_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedEmailIsRejectedByBeanValidationAs400() throws Exception {
        mvc.perform(login("not-an-email", RAW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankPasswordIsRejectedByBeanValidationAs400() throws Exception {
        mvc.perform(login("trader@db.com", ""))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email,
                                                                                             String password) {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    /** AppUser is deliberately setter-free (see its Javadoc), so seed it reflectively. */
    private AppUser user(String email, String rawPassword, String role, boolean enabled) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "email", email);
        ReflectionTestUtils.setField(u, "passwordHash", encoder.encode(rawPassword));
        ReflectionTestUtils.setField(u, "role", role);
        ReflectionTestUtils.setField(u, "enabled", enabled);
        return u;
    }
}
