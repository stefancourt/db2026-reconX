package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * TICKET-ADV050 — JPA entity Counterparty (one of the 8 entities of ADV006).
 */
@Entity
@Table(name = "counterparties")
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "lei_code", nullable = false, unique = true, length = 20)
    private String leiCode;

    @Column(nullable = false, length = 10)
    private String region;

    @Column(name = "created_at")
    private Instant createdAt;

    public Counterparty() {}

    public Long getId()           { return id; }
    public String getName()       { return name; }
    public String getLeiCode()    { return leiCode; }
    public String getRegion()     { return region; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name)       { this.name = name; }
    public void setLeiCode(String lei)     { this.leiCode = lei; }
    public void setRegion(String region)   { this.region = region; }
}
