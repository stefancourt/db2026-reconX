// frontend/src/components/TradeRow.jsx
import React from 'react';

function TradeRowImpl({ trade, onClick }) {
  return (
    <tr onClick={() => onClick(trade.id)}>
      <td>{trade.tradeRef}</td>
      <td>{trade.instrument}</td>
      <td>{trade.quantity}</td>
      <td>{trade.price}</td>
      <td><span className={`status-pill ${trade.status.toLowerCase()}`}>{trade.status}</span></td>
    </tr>
  );
}

// Custom equality — only the fields we actually render
function areEqual(prev, next) {
  return prev.trade.id      === next.trade.id
      && prev.trade.status  === next.trade.status
      && prev.trade.price   === next.trade.price
      && prev.onClick       === next.onClick;
}

export const TradeRow = React.memo(TradeRowImpl, areEqual);
