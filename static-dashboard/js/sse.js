// TICKET-ADV106 / ADV107 — EventSource live feed with prepend + slide-in animation.
(function () {
  const feed = document.getElementById('trade-feed');
  if (!feed) return;

  // Hardcoded demo events for the static dashboard (no backend required).
  // Replace with: const sse = new EventSource('/api/v1/trades/stream');
  const demoEvents = [
    { tradeRef: 'EQU-20260603-0001', symbol: 'SAP.DE',  qty: 1000, price: 125.50, status: 'MATCHED' },
    { tradeRef: 'FX-20260603-0001',  symbol: 'EUR/USD', qty: 1_000_000, price: 1.0852, status: 'PENDING' },
    { tradeRef: 'EQU-20260603-0002', symbol: 'AAPL',    qty: 500,  price: 178.20, status: 'BREAK' },
  ];

  function prepend(trade) {
    const el = document.createElement('article');
    el.className = 'trade-card trade-card--' + trade.status.toLowerCase();
    el.innerHTML = `
      <strong>${trade.tradeRef}</strong>
      <span> ${trade.symbol} </span>
      <span> qty=${trade.qty} </span>
      <span> price=${trade.price} </span>
      <span> [${trade.status}]</span>`;
    feed.prepend(el);
  }

  demoEvents.forEach((e, i) => setTimeout(() => prepend(e), 500 * i));
})();
