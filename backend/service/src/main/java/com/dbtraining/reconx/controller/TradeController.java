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