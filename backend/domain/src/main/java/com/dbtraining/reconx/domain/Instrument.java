package com.dbtraining.reconx.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
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

    public Long getId()                    { return id; }
    public String getSymbol()              { return symbol; }
    public String getName()                { return name; }
    public AssetClass getAssetClass()      { return assetClass; }
    public String getCurrency()            { return currency; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getIsin()                { return isin; }

    public void setSymbol(String v)                  { this.symbol = v; }
    public void setName(String v)                    { this.name = v; }
    public void setAssetClass(AssetClass v)          { this.assetClass = v; }
    public void setCurrency(String v)                { this.currency = v; }
    public void setMetadata(Map<String, Object> v)   { this.metadata = v; }
    public void setIsin(String v)                    { this.isin = v; }
}