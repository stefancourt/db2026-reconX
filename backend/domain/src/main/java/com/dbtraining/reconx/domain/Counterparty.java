package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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
 *
 * <p>Lombok: {@code @Getter} at class level, {@code @Setter} only on the three
 * caller-writable columns. {@code id} and {@code createdAt} are written by the
 * database and the auditing listener respectively, so they stay read-only. No
 * {@code @Data} — see the entity policy on {@link Trade}.
 */
@Entity
@Table(name = "counterparties")
@EntityListeners(AuditingEntityListener.class)
@Getter
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Column(name = "lei_code", nullable = false, unique = true, length = 20)
    private String leiCode;

    @Setter
    @Column(nullable = false, length = 10)
    private String region;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Counterparty() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Counterparty other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
