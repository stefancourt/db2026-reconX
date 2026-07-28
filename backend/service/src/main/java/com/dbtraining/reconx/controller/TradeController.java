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