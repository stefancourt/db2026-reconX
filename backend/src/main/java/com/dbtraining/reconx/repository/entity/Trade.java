package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * ============================================================================
 * TICKET-ADV050 — Trade JPA entity (with @ManyToOne, @CreatedDate, @LastModifiedDate)
 * TICKET-ADV052 — Hibernate Envers @Audited (auto rev table — see Day 4 guide)
 * TICKET-ADV067 — Soft delete via @SQLRestriction (filters deleted rows on SELECT)
 *
 * WHAT:    Persistent representation of a trade. Maps to the trades table
 *          declared in 002-schema.xml.
 * HOW:     ManyToOne LAZY to Counterparty and Instrument keeps the row
 *          fetch tight; the service layer asks for the relation only when
 *          it needs it.
 * WHY:     This is the durable record. The domain {@code TradeType} sealed
 *          hierarchy is the in-memory shape used by reconciliation; this
 *          entity is the on-disk shape used by JPA. The mapper between the
 *          two lives in {@code TradeMapper}.
 * OBSERVE: After a save, the trade row has created_at set by Spring Data,
 *          and a row appears in the Envers revision table.
 * ============================================================================
 */
@Entity
@Table(name = "trades")
@EntityListeners(AuditingEntityListener.class)
// @org.hibernate.envers.Audited                  // re-enable when envers tables are migrated
@SQLRestriction("deleted_at IS NULL")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_ref", nullable = false, unique = true, length = 30)
    private String tradeRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(name = "asset_class", nullable = false, length = 20)
    private String assetClass;

    @Column(nullable = false, length = 4)
    private String side;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private Instant modifiedAt;

    public Trade() {}

    /** Soft-delete: set deletedAt so @SQLRestriction filters this out. */
    public void softDelete() { this.deletedAt = Instant.now(); }

    public Long getId()                  { return id; }
    public String getTradeRef()          { return tradeRef; }
    public Instrument getInstrument()    { return instrument; }
    public Counterparty getCounterparty(){ return counterparty; }
    public String getAssetClass()        { return assetClass; }
    public String getSide()              { return side; }
    public BigDecimal getQuantity()      { return quantity; }
    public BigDecimal getPrice()         { return price; }
    public LocalDate getTradeDate()      { return tradeDate; }
    public String getStatus()            { return status; }
    public Instant getDeletedAt()        { return deletedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getModifiedAt()       { return modifiedAt; }

    public void setTradeRef(String v)         { this.tradeRef = v; }
    public void setInstrument(Instrument v)   { this.instrument = v; }
    public void setCounterparty(Counterparty v){ this.counterparty = v; }
    public void setAssetClass(String v)       { this.assetClass = v; }
    public void setSide(String v)             { this.side = v; }
    public void setQuantity(BigDecimal v)     { this.quantity = v; }
    public void setPrice(BigDecimal v)        { this.price = v; }
    public void setTradeDate(LocalDate v)     { this.tradeDate = v; }
    public void setStatus(String v)           { this.status = v; }
}
