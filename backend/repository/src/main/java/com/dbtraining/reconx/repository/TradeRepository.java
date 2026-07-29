package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ============================================================================
 * TICKET-ADV055 — Custom JPQL filter query
 * TICKET-ADV056 — Specification-based dynamic queries (JpaSpecificationExecutor)
 * TICKET-ADV057 — Pageable / Page<T> for paginated list endpoints
 * ============================================================================
 */
public interface TradeRepository
        extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    /** Derived query — Spring Data builds the SQL from the method name alone. */
    Optional<Trade> findByTradeRef(String tradeRef);

    /**
     * Date range is mandatory; status and counterparty are optional.
     *
     * <p>The {@code (:param IS NULL OR ...)} idiom lets one query serve every
     * combination of filters. It works, but the predicate is not sargable — on a
     * large table it degrades to a scan, which is why ADV056 replaces it with
     * composable {@code Specification}s.
     */
    @Query("""
        SELECT t FROM Trade t
        WHERE t.tradeDate BETWEEN :from AND :to
          AND (:status IS NULL OR t.status = :status)
          AND (:counterpartyId IS NULL OR t.counterparty.id = :counterpartyId)
        """)
    Page<Trade> findByFilters(@Param("from")           LocalDate from,
                              @Param("to")             LocalDate to,
                              @Param("status")         TradeStatus status,
                              @Param("counterpartyId") Long counterpartyId,
                              Pageable pageable);

    /** Derived count. The property is an enum, so the argument is the enum too. */
    long countByStatus(TradeStatus status);
}
