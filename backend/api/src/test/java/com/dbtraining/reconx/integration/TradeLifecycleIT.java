package com.dbtraining.reconx.integration;

import com.dbtraining.reconx.domain.ReconBreak;
import com.dbtraining.reconx.repository.ReconBreakRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV078 — Full lifecycle integration test with Testcontainers
 *
 * WHAT:    Boots the real application on a random port against a real
 *          postgres:16-alpine, then drives login -> create -> list -> patch
 *          -> recon run -> resolve over actual HTTP.
 * HOW:     @Testcontainers owns the container lifecycle; @ServiceConnection
 *          hands Spring the JDBC coordinates (no @DynamicPropertySource).
 *          @TestMethodOrder + @Order make the steps a single ordered
 *          transaction script; the JWT and the ids created along the way live
 *          in static fields so later steps can use them.
 * WHY:     This is the load-bearing end-to-end test for Workshop 5 — it is the
 *          only thing that proves Liquibase + JWT + RBAC + every controller
 *          from ADV063-ADV074 wire together on real Postgres.
 * OBSERVE: `docker ps` mid-run shows postgres:16-alpine; it is reaped when the
 *          class finishes. "Could not find a valid Docker environment" means
 *          Docker Desktop is not running.
 *
 * NOTE ON THE STUDENT GUIDE'S REFERENCE SOLUTION — three deviations, all
 * forced by the actual codebase:
 *   1. The login body is {"email": ...}, not {"username": ...} — see
 *      LoginRequest, whose field is @Email-validated.
 *   2. The context-path is /api (application.yml), so every URL below is
 *      "/api" + the controller mapping.
 *   3. 008-seed.xml seeds counterparties, instruments and users — but no
 *      trades and no recon_breaks. The guide's "break id 1 is seeded"
 *      assumption is false, so resolveBreak() inserts its own OPEN break
 *      against the trade it just created and resolves that.
 *
 * STATUS:  loginAsAdmin / createTrade / triggerRecon / resolveBreak pass today.
 *          getTradeBack is RED until TradeService.list is written (ADV055) and
 *          patchStatus is RED until TradeService.updateStatus is written
 *          (ADV066) — both still throw UnsupportedOperationException, which
 *          the controller surfaces as HTTP 500. Treat those two methods as the
 *          executable spec for ADV055 and ADV066.
 * ============================================================================
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradeLifecycleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort int port;

    @Autowired ReconBreakRepository breaks;

    static String token;
    static Long createdId;
    static String reconJobId;
    static Long breakId;

    /**
     * The default SimpleClientHttpRequestFactory (HttpURLConnection) cannot send
     * PATCH — step 4 would die with "Invalid HTTP method" before it ever reached
     * the server. Apache HttpClient can.
     */
    private final RestTemplate http = new RestTemplate(new HttpComponentsClientHttpRequestFactory());

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @Test @Order(1)
    void loginAsAdmin() {
        // admin@db.com / admin123 — BCrypt hash seeded by 008-seed.xml.
        var body = """
                {"email":"admin@db.com","password":"admin123"}
                """;
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        var resp = http.postForEntity(url("/auth/login"), new HttpEntity<>(body, json), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = resp.getBody().get("token").asText();
        assertThat(token).isNotBlank();
    }

    @Test @Order(2)
    void createTrade() {
        // tradeRef regex: ^[A-Z]{3}-\d{8}-\d{4}$. assetClass and side are @NotBlank on
        // TradeRequest; status is server-side and must NOT appear in the request body.
        var body = """
                {"tradeRef":"INT-20260315-0001","instrumentId":1,"counterpartyId":1,
                 "assetClass":"EQUITY","side":"BUY",
                 "quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}
                """;
        var resp = http.exchange(url("/v1/trades"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
        createdId = resp.getBody().get("id").asLong();
        assertThat(resp.getBody().get("status").asText()).isEqualTo("PENDING");
    }

    @Test @Order(3)
    void getTradeBack() {
        var resp = http.exchange(url("/v1/trades?status=PENDING"), HttpMethod.GET,
                new HttpEntity<>(authHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(4)
    void patchStatus() {
        var body = """
                {"status":"MATCHED"}
                """;
        var resp = http.exchange(url("/v1/trades/" + createdId + "/status"), HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status").asText()).isEqualTo("MATCHED");
    }

    @Test @Order(5)
    void triggerRecon() {
        var body = """
                {"from":"2026-03-01","to":"2026-03-31"}
                """;
        var resp = http.exchange(url("/v1/recon/run"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        reconJobId = resp.getBody().get("jobId").asText();
        assertThat(reconJobId).isNotBlank();
    }

    @Test @Order(6)
    void resolveBreak() {
        // No break is seeded (see the class NOTE), so create the OPEN break this
        // step is about to resolve. The assertion is on the HTTP contract of
        // PUT /recon/results/{id}/resolve, not on how the break got there.
        ReconBreak open = new ReconBreak();
        open.setTradeId(createdId);
        open.setDiscrepancyType("VALUE_MISMATCH");
        breakId = breaks.saveAndFlush(open).getId();

        var body = """
                {"note":"Confirmed via counterparty email on 2026-03-16."}
                """;
        var resp = http.exchange(url("/v1/recon/results/" + breakId + "/resolve"), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resp.getBody().get("resolutionNote").asText())
                .isEqualTo("Confirmed via counterparty email on 2026-03-16.");
    }
}
