package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.EquityTrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * ============================================================================
 * TICKET-ADV035 — VWAP via a custom Collector
 *
 * WHAT:    Collector<EquityTrade, ?, BigDecimal> computing the volume-weighted
 *          average price: SUM(price * quantity) / SUM(quantity).
 * HOW:     All four Collector contributions written by hand — supplier,
 *          accumulator, combiner, finisher — plus Characteristics.UNORDERED.
 * WHY:     VWAP needs two running totals, so the reduction cannot be expressed
 *          with a single BigDecimal identity. Writing the four methods once
 *          shows why the combiner must return a fresh accumulator: with a
 *          parallel stream both inputs are live and mutating either one
 *          corrupts the other half of the split.
 * OBSERVE: stream() and parallelStream() over the same input return identical
 *          BigDecimal results; empty input returns BigDecimal.ZERO instead of
 *          throwing ArithmeticException on a divide by zero.
 * ============================================================================
 */
public final class VwapCollector implements Collector<EquityTrade, VwapCollector.Acc, BigDecimal> {

    /** Result scale and rounding — fixed so serial and parallel agree exactly. */
    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Mutable two-field accumulator: weighted notional and total quantity. */
    static final class Acc {
        private BigDecimal weighted = BigDecimal.ZERO;
        private BigDecimal quantity = BigDecimal.ZERO;
    }

    public static VwapCollector toVwap() {
        return new VwapCollector();
    }

    @Override
    public Supplier<Acc> supplier() {
        return Acc::new;
    }

    @Override
    public BiConsumer<Acc, EquityTrade> accumulator() {
        return (acc, trade) -> {
            if (trade == null) return;
            acc.weighted = acc.weighted.add(trade.price().multiply(trade.quantity()));
            acc.quantity = acc.quantity.add(trade.quantity());
        };
    }

    /**
     * Returns a NEW accumulator holding the sums of both inputs. Returning a
     * mutated left or right is the classic parallelism bug — the other half of
     * the split may still hold a reference to it.
     */
    @Override
    public BinaryOperator<Acc> combiner() {
        return (left, right) -> {
            Acc merged = new Acc();
            merged.weighted = left.weighted.add(right.weighted);
            merged.quantity = left.quantity.add(right.quantity);
            return merged;
        };
    }

    @Override
    public Function<Acc, BigDecimal> finisher() {
        return acc -> acc.quantity.signum() == 0
                ? BigDecimal.ZERO
                : acc.weighted.divide(acc.quantity, SCALE, ROUNDING);
    }

    /**
     * UNORDERED only — VWAP is a commutative aggregate. Not CONCURRENT (the
     * accumulator is not thread-safe) and not IDENTITY_FINISH (the finisher
     * divides, so Acc is not the result type).
     */
    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }
}
