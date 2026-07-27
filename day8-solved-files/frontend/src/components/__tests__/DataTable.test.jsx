// RTL test against the DataTable compound component.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import DataTable from '../DataTable.jsx';

describe('<DataTable>', () => {
  it('renders columns and rows', () => {
    render(
      <DataTable>
        <DataTable.Header columns={[{ key: 'a', label: 'Alpha' }, { key: 'b', label: 'Beta' }]} />
        <DataTable.Body rows={[{ id: 1 }, { id: 2 }]} render={(r) => <span>row {r.id}</span>} />
      </DataTable>
    );
    expect(screen.getByRole('button', { name: 'Alpha' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Beta' })).toBeInTheDocument();
    expect(screen.getByText('row 1')).toBeInTheDocument();
    expect(screen.getByText('row 2')).toBeInTheDocument();
  });

  it('invokes onSortChange when a header is clicked', async () => {
    const onSortChange = vi.fn();
    render(
      <DataTable onSortChange={onSortChange}>
        <DataTable.Header columns={[{ key: 'a', label: 'Alpha' }]} />
        <DataTable.Body rows={[]} render={() => null} />
      </DataTable>
    );
    await userEvent.click(screen.getByText('Alpha'));
    expect(onSortChange).toHaveBeenCalledWith('a');
  });
});
