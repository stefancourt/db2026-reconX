// useMemo for portfolio-value calc + useTradeStream live feed.
import React, { useMemo } from 'react';
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

function Dashboard({ trades: tradesProp }) {
  const stream = useTradeStream();
  // Prefer explicit prop (used by RTL tests) over the live SSE feed.
  const trades = tradesProp ?? stream.trades;
  const isConnected = tradesProp ? true : stream.isConnected;

  const portfolioValue = useMemo(
    () => trades.reduce((sum, t) => sum + (Number(t.quantity) * Number(t.price) || 0), 0),
    [trades]
  );

  const { matched, unmatched, breaks } = useMemo(() => {
    let m = 0;
    let u = 0;
    let b = 0;
    for (const t of trades) {
      if (t.status === 'MATCHED') m++;
      else if (t.status === 'UNMATCHED') { u++; b++; }
      else if (t.status === 'DISPUTED') b++;
    }
    return { matched: m, unmatched: u, breaks: b };
  }, [trades]);

  return (
    <section>
      <h2>Dashboard</h2>
      <div className="stat-grid">
        <StatCard label="Portfolio value (USD)" value={portfolioValue.toLocaleString()} />
        <StatCard label="Trades streamed" value={trades.length} />
        <StatCard label="Matched trades" value={matched} />
        <StatCard label="Unmatched trades" value={unmatched} />
        <StatCard label="Open breaks" value={breaks} />
      </div>
      <div role="status" aria-live="polite">
        SSE: {isConnected ? 'connected' : 'disconnected'}
      </div>
    </section>
  );
}

export default withAuth(Dashboard);
