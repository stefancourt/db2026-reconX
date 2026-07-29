package com.dbtraining.reconx.service;                                        
                                                                            
import com.dbtraining.reconx.repository.CounterpartyRepository;               
import com.dbtraining.reconx.repository.TradeRepository;                      
import com.dbtraining.reconx.domain.Counterparty;
import lombok.RequiredArgsConstructor;

import java.util.NoSuchElementException;

@RequiredArgsConstructor
public class TradeLookupService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;

    public Counterparty counterpartyForTradeRef(String tradeRef) {
        return tradeRepo.findByTradeRef(tradeRef)
                .map(trade -> trade.getCounterparty())
                .orElseThrow(() -> new NoSuchElementException(
                        "No counterparty resolvable for trade " + tradeRef));
    }

}
