package com.dbtraining.reconx.domain;

/**
 * Asset-class discriminator for the persisted {@link Instrument}.
 *
 * <p>Stored as {@code @Enumerated(EnumType.STRING)}, so these constant names are the
 * literal on-disk values — renaming one is a data migration, not a refactor. They are
 * therefore taken from the {@code asset_class} column seeded by
 * {@code data/instruments.csv}, which uses EQUITY, FIXED_INCOME and FX.
 *
 * <p>Note the vocabulary gap with the in-memory model: what {@code TradeType.AssetClass}
 * calls {@code BOND}, the database calls {@code FIXED_INCOME}. Anything translating
 * between a {@code TradeType} and a persisted row has to map across that.
 */
public enum AssetClass {
    EQUITY,
    FX,
    FIXED_INCOME,
    DERIVATIVE
}
