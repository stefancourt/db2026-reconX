package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.dbtraining.reconx.domain.TradeStatus;

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
@RequiredArgsConstructor
public class TradeController {

    private final TradeService service;
    private final TradeMapper mapper;

    @GetMapping
    @Operation(summary = "List trades — paginated, filterable, sortable")
    public PagedResponse<TradeResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long counterpartyId,
            @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Trade> page = service.list(from, to, status, counterpartyId, pageable);
        return PagedResponse.from(page, mapper::toResponse);
    }

    @PostMapping
    @Operation(summary = "Create a trade")
    public ResponseEntity<TradeResponse> create(@Valid @RequestBody TradeRequest req,
                                                @AuthenticationPrincipal Object principal) {
        String actor = String.valueOf(principal);
        Trade saved = service.create(req, actor);
        return ResponseEntity
            .created(URI.create("/api/v1/trades/" + saved.getId()))
            .body(mapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full update of a trade")
    public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeRequest req,
                                @AuthenticationPrincipal Object principal) {
        return mapper.toResponse(service.update(id, req, String.valueOf(principal)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update only the status field")
    public TradeResponse updateStatus(@PathVariable Long id,
                                      @RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal Object principal) {
        String status = body.get("status");
        return mapper.toResponse(service.updateStatus(id, status, String.valueOf(principal)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete (sets deleted_at)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal Object principal) {
        service.softDelete(id, String.valueOf(principal));
        return ResponseEntity.noContent().build();
    }
    @Deprecated(since = "v1.4.0", forRemoval = true)
    @GetMapping(value = "/old-search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "[DEPRECATED] Old search — use GET /api/v1/trades?status=... instead",
               deprecated = true)
    public ResponseEntity<Void> oldSearch(HttpServletResponse response) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", "Tue, 01 Dec 2026 00:00:00 GMT");
        response.setHeader("Link",
                "</api/v1/trades?status=PENDING>; rel=\"successor-version\"");
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
