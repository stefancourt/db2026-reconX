package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class TradeSpecification {

    private TradeSpecification() {}

    public static Specification<Trade> tradeDateBetween(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null)               return cb.lessThanOrEqualTo(root.get("tradeDate"), to);
            if (to == null)                 return cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
            return cb.between(root.get("tradeDate"), from, to);
        };
    }

    public static Specification<Trade> hasStatus(TradeStatus status) {
        return (root, q, cb) -> status == null
            ? cb.conjunction()
            : cb.equal(root.get("status"), status);
    }

    public static Specification<Trade> forCounterparty(Long counterpartyId) {
        return (root, q, cb) -> counterpartyId == null
            ? cb.conjunction()
            : cb.equal(root.get("counterparty").get("id"), counterpartyId);
    }

    public static Specification<Trade> refLike(String pattern) {
        return (root, q, cb) -> pattern == null || pattern.isBlank()
            ? cb.conjunction()
            : cb.like(root.get("tradeRef"), pattern + "%");
    }
}