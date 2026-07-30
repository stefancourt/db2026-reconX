// TICKET-ADV125 — RTL test: dashboard summary cards
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@context/ThemeContext.jsx';
import { AuthContext } from '@context/AuthContext.jsx';
import Dashboard from './Dashboard.jsx';

vi.mock('@hooks/useTradeStream.js', () => ({
  useTradeStream: () => ({
    trades: [
      { id: 1, tradeRef: 'TRD-2026-0001', quantity: 100, price: 250, status: 'MATCHED' },
      { id: 2, tradeRef: 'TRD-2026-0002', quantity: 50,  price: 251, status: 'UNMATCHED' },
    ],
    isConnected: false,
  }),
}));

function renderWithProviders(ui) {
  const user = { email: 'trader@db.com', role: 'TRADER' };
  return render(
    <AuthContext.Provider value={{ user, isLoading: false }}>
      <ThemeProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ThemeProvider>
    </AuthContext.Provider>
  );
}

describe('<Dashboard />', () => {
  it('shows summary cards', () => {
    renderWithProviders(<Dashboard />);

    expect(screen.getByRole('heading', { name: /portfolio value/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /trades streamed/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /matched/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /open breaks/i })).toBeInTheDocument();
  });
});
