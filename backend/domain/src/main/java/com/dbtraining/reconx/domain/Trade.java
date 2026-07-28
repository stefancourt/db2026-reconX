package com.dbtraining.reconx.domain;

import com.dbtraining.reconx.model.Side;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * ============================================================================
 * TICKET-ADV050 — Trade JPA entity (@ManyToOne, @CreatedDate, @LastModifiedDate)
 * TICKET-ADV052 — Hibernate Envers @Audited (audit tables created in 009-envers.xml)
 * TICKET-ADV067 — Soft delete via @SQLRestriction (filters deleted rows on SELECT)
 *
 * Maps the {@code trades} table declared in 002-schema.xml. Every NOT NULL column
 * in that changeset is mapped here — {@code asset_class} and {@code side} included,
 * otherwise an INSERT omits them and Postgres rejects the row.
 *
 * <p><b>Lombok policy for entities:</b> {@code @Getter} at class level, {@code @Setter}
 * per mutable field only. Never {@code @Data}, {@code @ToString} or
 * {@code @EqualsAndHashCode} — those walk every field, which touches the LAZY
 * {@code counterparty} and {@code instrument} proxies and turns a single
 * {@code HashSet.add} or a log line into an N+1 storm. The id-only
 * {@code equals}/{@code hashCode} below stays hand-written for that reason.
 * ============================================================================
 */
@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trades_trade_date", columnList = "trade_date"),
    @Index(name = "idx_trades_status",     columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
@Audited
@Getter
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "trade_ref", nullable = false, unique = true, length = 30)
    private String tradeRef;

    // Reference data is not itself audited — Envers stores only the FK id in the
    // audit row. Without NOT_AUDITED it demands @Audited on Counterparty/Instrument too.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Counterparty counterparty;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Instrument instrument;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    @Setter
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Setter
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Setter
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus status = TradeStatus.PENDING;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private Instant modifiedAt;

    public Trade() {}

    /** Soft-delete: set deletedAt so {@code @SQLRestriction} filters this row out. */
    public void softDelete() { this.deletedAt = Instant.now(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
