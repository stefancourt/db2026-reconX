package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ============================================================================
 * TICKET-ADV053 — TradeRequest DTO (POST body)
 * TICKET-ADV029 — JSR-380 validation annotations live on the DTO, not the entity
 *
 * WHY:    Putting @Pattern/@Positive/@NotNull on the JPA entity couples
 *         persistence to wire format. The DTO is the wire contract; validate
 *         it before mapping.
 * ============================================================================
 */
public record TradeRequest(
        @NotNull
        @Pattern(regexp = "^[A-Z]{3}-\\d{8}-\\d{4}$",
                 message = "tradeRef must match AAA-YYYYMMDD-NNNN")
        String tradeRef,

        @NotNull
        Long instrumentId,

        @NotNull
        Long counterpartyId,

        @NotBlank
        String assetClass,

        @NotBlank
        @Pattern(regexp = "^(BUY|SELL)$")
        String side,

        @NotNull @Positive
        BigDecimal quantity,

        @NotNull @PositiveOrZero
        BigDecimal price,

        @NotNull
        LocalDate tradeDate
) {}
