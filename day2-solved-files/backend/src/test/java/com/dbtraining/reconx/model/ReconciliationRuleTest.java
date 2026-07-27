package com.dbtraining.reconx.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationRuleTest {

    @ParameterizedTest(name = "rule={0} priceA={1} priceB={2} qtyA={3} qtyB={4} expected={5}")
    @CsvSource({
            "EXACT,                100.00, 100.00, 10, 10, true",
            "EXACT,                100.00, 100.01, 10, 10, false",
            "PRICE_TOLERANCE_1PCT, 100.00, 100.50, 10, 10, true",
            "PRICE_TOLERANCE_1PCT, 100.00, 102.00, 10, 10, false",
            "QTY_TOLERANCE_5UNITS, 100.00, 100.00, 10, 14, true",
            "QTY_TOLERANCE_5UNITS, 100.00, 100.00, 10, 16, false",
            "LOOSE,                100.00, 104.00, 10, 18, true"
    })
    void matches(ReconciliationRule rule, BigDecimal pa, BigDecimal pb,
                 BigDecimal qa, BigDecimal qb, boolean expected) {
        assertThat(rule.matches(pa, qa, pb, qb)).isEqualTo(expected);
    }
}
