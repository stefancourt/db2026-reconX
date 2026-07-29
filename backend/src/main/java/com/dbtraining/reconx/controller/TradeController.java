package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.service.TradeQueryService;



/**
 * ============================================================================
 * TICKET-ADV063-ADV067 — TradeController (full CRUD + filterable list)
 * TICKET-ADV080 — API versioning: every endpoint under /v1/
 *
 * Combined with the /api context-path from application.yml, full URLs are
 * /api/v1/trades, /api/v1/trades/{id} etc.
 * ============================================================================
 */
@RestController
@RequestMapping("/v1/trades")
@Tag(name = "trades", description = "Trade CRUD and search")
@SecurityRequirement(name = "bearerAuth")
public class TradeController {

    private final TradeService service;
    private final TradeMapper mapper;
    private final TradeQueryService queryService;

    public TradeController(TradeService service, TradeMapper mapper, TradeQueryService queryService) {
        this.service = service;
        this.mapper = mapper;
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "List trades — paginated, filterable, sortable")
    public PagedResponse<TradeResponse> list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TradeStatus status,
        @RequestParam(required = false) Long counterpartyId,
        @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        var page = queryService.search(from, to, status, counterpartyId, pageable);
        return PagedResponse.of(page, mapper::toResponse);
    }

    @PostMapping
    @Operation(summary = "Create a trade")
    public ResponseEntity<TradeResponse> create(@Valid @RequestBody TradeRequest req,
                                                @AuthenticationPrincipal Object principal) {
        // TODO(TICKET-ADV064): call service.create(req, actor), build a Location
        //   header at /api/v1/trades/{id}, and return 201 Created with the
        //   mapped TradeResponse body.
        throw new UnsupportedOperationException("TICKET-ADV064");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full update of a trade")
    public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeRequest req,
                                @AuthenticationPrincipal Object principal) {
        // TODO(TICKET-ADV065): delegate to service.update(id, req, actor) and
        //   map the updated entity through mapper.toResponse.
        throw new UnsupportedOperationException("TICKET-ADV065");
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update only the status field")
    public TradeResponse updateStatus(@PathVariable Long id,
                                      @RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal Object principal) {
        // TODO(TICKET-ADV066): read body.get("status") and call
        //   service.updateStatus(id, status, actor). Return mapper.toResponse(saved).
        throw new UnsupportedOperationException("TICKET-ADV066");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete (sets deleted_at)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal Object principal) {
        // TODO(TICKET-ADV067): service.softDelete(id, actor); return 204 No Content.
        throw new UnsupportedOperationException("TICKET-ADV067");
    }
}
