package com.dbtraining.reconx.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * Instrument reference data.
 *
 * <p>{@code @Setter} is class-level here with {@code AccessLevel.NONE} on the
 * generated id — the database owns that value. No {@code @Data}: see the entity
 * Lombok policy on {@link Trade}.
 */
@Entity
@Table(name = "instruments")
@Getter
@Setter
public class Instrument {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
    * JSONB metadata: tick size, lot size, exchange code, etc.
    * On H2 (dev profile) this stores as a CLOB; on Postgres it's true JSONB
    * and is queryable via the @> operator.
    */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(length = 12)
    private String isin;

    public Instrument() {}
}