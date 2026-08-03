package com.dbtraining.reconx.service;

import com.dbtraining.reconx.domain.AuditLogEntry;
import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TradeAggregator {

    private final AuditLogRepository auditRepo;
    private final ObjectMapper objectMapper;

    public TradeAggregator(AuditLogRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    public Optional<JsonNode> rebuild(String tradeRef) {
        List<AuditLogEntry> events = auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        JsonNode state = null;
        for (AuditLogEntry e : events) {
            switch (TradeEvent.EventType.valueOf(e.getEventType())) {
                case TRADE_CREATED, TRADE_UPDATED -> state = readState(e.getAfterState());
                case TRADE_CANCELLED              -> state = null;
            }
        }
        return Optional.ofNullable(state);
    }

    // audit_log.after_state is stored as JSON text, so each replayed row is
    // parsed back into a JsonNode before it becomes the running state.
    private JsonNode readState(String afterState) {
        if (afterState == null || afterState.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(afterState);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unparseable after_state in audit_log", ex);
        }
    }
}
