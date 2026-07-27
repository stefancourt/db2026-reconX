package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Counterparty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {
    Optional<Counterparty> findByLeiCode(String leiCode);
}
