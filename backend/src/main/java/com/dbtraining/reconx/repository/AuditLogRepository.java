package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findByTradeRefOrderByEventTimestampAsc(String tradeRef);
}
