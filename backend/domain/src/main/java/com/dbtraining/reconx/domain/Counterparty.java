package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Counterparty on the other side of a {@link Trade}.
 *
 * <p>Maps the {@code counterparties} table declared in 002-schema.xml and seeded from
 * {@code data/counterparties.csv}. Column lengths match the changelog exactly so
 * {@code hibernate.ddl-auto: validate} passes.
 */
@Entity
@Table(name = "counterparties")
@EntityListeners(AuditingEntityListener.class)
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

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Counterparty() {}

    public Long getId()           { return id; }
    public String getName()       { return name; }
    public String getLeiCode()    { return leiCode; }
    public String getRegion()     { return region; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String v)    { this.name = v; }
    public void setLeiCode(String v) { this.leiCode = v; }
    public void setRegion(String v)  { this.region = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Counterparty other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
