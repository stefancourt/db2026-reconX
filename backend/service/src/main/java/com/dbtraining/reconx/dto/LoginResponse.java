package com.dbtraining.reconx.dto;

/** TICKET-ADV072 — JWT envelope returned to clients. */
public record LoginResponse(String token, String tokenType, long expiresInSeconds, String role) {}
