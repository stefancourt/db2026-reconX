package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReconRunRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        Long counterpartyId
) {}