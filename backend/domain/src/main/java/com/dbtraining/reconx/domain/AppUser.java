package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/**
 * TICKET-ADV074 — Users for JWT-backed RBAC. Named AppUser to avoid clash
 * with Spring Security's User interface.
 *
 * <p>{@code @Getter} only — no setters by design. {@code passwordHash} must never
 * be reassigned by application code, and {@code @ToString} is deliberately absent
 * so the hash cannot leak into a log line.
 */
@Entity
@Table(name = "users")
@Getter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at")
    private Instant createdAt;

    public AppUser() {}
}
