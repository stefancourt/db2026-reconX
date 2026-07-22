// TICKET-ADV120 — useMemo for portfolio-value calc.
// TICKET-ADV116 — useTradeStream live feed.
import React from 'react';
import { withAuth } from '@components/withAuth.jsx';
import { useTradeStream } from '@hooks/useTradeStream.js';

function StatCard({ label, value }) {
  return (
    <article className="stat-card">
      <h3>{label}</h3>
      <p>{value}</p>
    </article>
  );
}

function Dashboard() {
  const { trades, isConnected } = useTradeStream();

  // TODO(TICKET-ADV120): use useMemo to compute `portfolioValue` =
  //                     sum(trades[i].quantity * trades[i].price).
  //                     Memoise on `trades` so it doesn't recompute every render.

  // TODO(TICKET-ADV120): derive `matched` (status === 'MATCHED') and
  //                     `breaks` (status in ['UNMATCHED','DISPUTED']) counts.

  return (
    <section>
      <h2>Dashboard</h2>
      <div className="stat-grid">
        {/* TODO(TICKET-ADV120): render four <StatCard>s — Portfolio value,
            Trades streamed, Matched, Open breaks. */}
      </div>
      <div role="status" aria-live="polite">
        SSE: {isConnected ? 'connected' : 'disconnected'}
      </div>
    </section>
  );
}

export default withAuth(Dashboard);
