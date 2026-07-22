package com.dbtraining.reconx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** TICKET-ADV053 — TradeResponse DTO returned by GET / POST endpoints. */
public record TradeResponse(
        Long id,
        String tradeRef,
        Long instrumentId,
        String instrumentSymbol,
        Long counterpartyId,
        String counterpartyName,
        String assetClass,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        LocalDate tradeDate,
        String status,
        Instant createdAt,
        Instant modifiedAt
) {}
