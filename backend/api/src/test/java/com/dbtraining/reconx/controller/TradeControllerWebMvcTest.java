//Ticket-ADV075 and 076 Tejas

package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.security.JwtAuthenticationFilter;
import com.dbtraining.reconx.security.JwtTokenProvider;
import com.dbtraining.reconx.security.SecurityConfig;
import com.dbtraining.reconx.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A slice test only sees the beans it is told about. {@code SecurityConfig} and
 * {@code JwtAuthenticationFilter} are imported explicitly — without them the slice
 * falls back to Boot's default "everything authenticated" chain and the VIEWER
 * case would return 200 instead of 403, i.e. the RBAC assertion would be vacuous.
 */
@WebMvcTest(TradeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class TradeControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TradeService tradeService;
    @MockitoBean private TradeMapper tradeMapper;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    private TradeRequest validRequest() {
        // Field order matches the current TradeRequest record:
        // (tradeRef, instrumentId, counterpartyId, assetClass, side, quantity, price, tradeDate).
        // tradeRef regex: ^[A-Z]{3}-\d{8}-\d{4}$. Status is NOT a request field — it is set server-side.
        return new TradeRequest(
                "TRD-20260315-9999",
                1L,
                1L,
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("245.50"),
                LocalDate.now());
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void testCreateTrade_authenticated_returns201() throws Exception {
        // The controller calls service.create(req, actor) -> Trade, then
        // mapper.toResponse(trade) -> TradeResponse. Both are mocked here.
        Trade saved = new Trade();
        saved.setTradeRef("TRD-20260315-9999");
        when(tradeService.create(any(), any())).thenReturn(saved);

        Instant now = Instant.now();
        // Field order matches the current TradeResponse record:
        // (id, tradeRef, counterpartyId, counterpartyName, instrumentId, instrumentSymbol,
        //  quantity, price, tradeDate, status, createdAt, modifiedAt).
        when(tradeMapper.toResponse(any())).thenReturn(
                new TradeResponse(
                        42L,
                        "TRD-20260315-9999",
                        1L,
                        "Apex Brokers Inc",
                        1L,
                        "SAP.DE",
                        new BigDecimal("100.0000"),
                        new BigDecimal("245.50"),
                        LocalDate.now(),
                        "PENDING",
                        now,
                        now));

        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/trades/")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.tradeRef").value("TRD-20260315-9999"));
    }

    @Test
    void testCreateTrade_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/v1/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void testCreateTrade_viewerRole_returns403() throws Exception {
    mockMvc.perform(post("/v1/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
                    .with(csrf()))
            .andExpect(status().isForbidden());
    }
}
