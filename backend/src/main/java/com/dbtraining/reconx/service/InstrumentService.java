package com.dbtraining.reconx.service;

import com.dbtraining.reconx.exception.InvalidTradeException;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.entity.Instrument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * TICKET-ADV081 — @Cacheable on findBySymbol (cache name "instruments").
 * TICKET-ADV082 — TTL configured in application.yml (caffeine spec).
 *
 * Symbol lookup is hot — most requests touch the cache, not the DB.
 */
@Service
public class InstrumentService {

    private final InstrumentRepository repo;

    public InstrumentService(InstrumentRepository repo) { this.repo = repo; }

    @Cacheable("instruments")
    public Instrument findBySymbol(String symbol) {
        // TODO(TICKET-ADV081): return repo.findBySymbol(symbol)
        //   .orElseThrow(() -> new InvalidTradeException("Unknown instrument symbol: " + symbol)).
        //   The @Cacheable annotation above is what makes the second call cheap —
        //   verify the cache hit-rate via /actuator/caches once you wire this up.
        throw new UnsupportedOperationException("TICKET-ADV081");
    }
}
