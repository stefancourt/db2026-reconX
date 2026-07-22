package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * TICKET-ADV074 — Users for JWT-backed RBAC. Named AppUser to avoid clash
 * with Spring Security's User interface.
 */
@Entity
@Table(name = "users")
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

    public Long getId()           { return id; }
    public String getEmail()      { return email; }
    public String getPasswordHash(){ return passwordHash; }
    public String getRole()       { return role; }
    public Boolean getEnabled()   { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
