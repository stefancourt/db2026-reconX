package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** TICKET-ADV072 — POST /api/auth/login body. */
public record LoginRequest(@Email @NotBlank String email,
                           @NotBlank String password) {}
