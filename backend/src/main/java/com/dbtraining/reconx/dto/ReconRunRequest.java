package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** TICKET-ADV068 — POST /api/v1/recon/run body. */
public record ReconRunRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        Long counterpartyId
) {}
