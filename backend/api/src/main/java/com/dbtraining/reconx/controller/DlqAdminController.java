package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.domain.DlqMessage;
import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * TICKET-ADV136 — DLQ inbox + replay endpoint
 *
 * WHAT:    GET  /api/v1/admin/dlq          — list quarantined messages.
 *          POST /api/v1/admin/dlq/replay   — re-publish exactly one of them.
 * WHY:     The operator's escape hatch once the bug behind the failure is
 *          fixed. Replay is one event at a time by eventId on purpose: a
 *          bulk replay against an unfixed bug just re-fills the DLQ.
 * OBSERVE: dryRun=true returns the payload that would be replayed and
 *          changes nothing; a real run re-publishes to `trade-events` and
 *          removes the inbox row.
 * ============================================================================
 */
@RestController
@RequestMapping("/v1/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-dlq")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DlqAdminController {

    private final DlqMessageRepository repo;
    private final TradeEventProducer producer;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "List quarantined DLQ messages, newest first")
    public List<DlqMessage> list() {
        return repo.findAllByOrderByFirstSeenDesc();
    }

    @PostMapping("/replay")
    @Operation(summary = "Re-publish a single DLQ message back onto trade-events")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestParam String eventId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        DlqMessage msg = repo.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No DLQ message: " + eventId));

        if (dryRun) {
            return ResponseEntity.ok(Map.of(
                    "dryRun", true,
                    "eventId", eventId,
                    "wouldReplayTo", msg.getOriginalTopic(),
                    "tradeRef", String.valueOf(msg.getTradeRef()),
                    "payload", String.valueOf(msg.getPayload())));
        }

        producer.publish(deserialise(msg));
        repo.delete(msg);

        return ResponseEntity.ok(Map.of(
                "replayed", true,
                "eventId", eventId,
                "topic", msg.getOriginalTopic()));
    }

    private TradeEvent deserialise(DlqMessage msg) {
        if (msg.getPayload() == null) {
            // A poison pill never became a TradeEvent, so there is nothing to
            // replay through the typed producer — that needs a manual fix.
            throw new IllegalArgumentException(
                    "DLQ message " + msg.getEventId() + " has no replayable payload");
        }
        try {
            return objectMapper.readValue(msg.getPayload(), TradeEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Corrupt DLQ payload for eventId " + msg.getEventId(), e);
        }
    }
}
