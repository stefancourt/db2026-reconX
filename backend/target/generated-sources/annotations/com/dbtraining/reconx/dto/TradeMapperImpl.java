package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-23T21:46:19+0530",
    comments = "version: 1.6.2, compiler: javac, environment: Java 21.0.7 (Amazon.com Inc.)"
)
@Component
public class TradeMapperImpl implements TradeMapper {

    @Override
    public TradeResponse toResponse(Trade trade) {
        if ( trade == null ) {
            return null;
        }

        Long instrumentId = null;
        String instrumentSymbol = null;
        Long counterpartyId = null;
        String counterpartyName = null;
        Long id = null;
        String tradeRef = null;
        String assetClass = null;
        String side = null;
        BigDecimal quantity = null;
        BigDecimal price = null;
        LocalDate tradeDate = null;
        String status = null;
        Instant createdAt = null;
        Instant modifiedAt = null;

        instrumentId = tradeInstrumentId( trade );
        instrumentSymbol = tradeInstrumentSymbol( trade );
        counterpartyId = tradeCounterpartyId( trade );
        counterpartyName = tradeCounterpartyName( trade );
        id = trade.getId();
        tradeRef = trade.getTradeRef();
        assetClass = trade.getAssetClass();
        side = trade.getSide();
        quantity = trade.getQuantity();
        price = trade.getPrice();
        tradeDate = trade.getTradeDate();
        status = trade.getStatus();
        createdAt = trade.getCreatedAt();
        modifiedAt = trade.getModifiedAt();

        TradeResponse tradeResponse = new TradeResponse( id, tradeRef, instrumentId, instrumentSymbol, counterpartyId, counterpartyName, assetClass, side, quantity, price, tradeDate, status, createdAt, modifiedAt );

        return tradeResponse;
    }

    private Long tradeInstrumentId(Trade trade) {
        Instrument instrument = trade.getInstrument();
        if ( instrument == null ) {
            return null;
        }
        return instrument.getId();
    }

    private String tradeInstrumentSymbol(Trade trade) {
        Instrument instrument = trade.getInstrument();
        if ( instrument == null ) {
            return null;
        }
        return instrument.getSymbol();
    }

    private Long tradeCounterpartyId(Trade trade) {
        Counterparty counterparty = trade.getCounterparty();
        if ( counterparty == null ) {
            return null;
        }
        return counterparty.getId();
    }

    private String tradeCounterpartyName(Trade trade) {
        Counterparty counterparty = trade.getCounterparty();
        if ( counterparty == null ) {
            return null;
        }
        return counterparty.getName();
    }
}
