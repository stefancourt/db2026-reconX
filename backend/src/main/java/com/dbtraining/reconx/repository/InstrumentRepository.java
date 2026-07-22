package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    Optional<Instrument> findBySymbol(String symbol);
}
