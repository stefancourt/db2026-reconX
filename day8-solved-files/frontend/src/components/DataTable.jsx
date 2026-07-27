// Compound <DataTable> with Header / Body / Pagination subcomponents.
import React, { createContext, useContext } from 'react';

const DataTableContext = createContext({ sort: null, page: 0, size: 20, onSortChange: undefined });

export default function DataTable({ children, sort, page = 0, size = 20, onSortChange }) {
  return (
    <DataTableContext.Provider value={{ sort, page, size, onSortChange }}>
      <div className="data-table">{children}</div>
    </DataTableContext.Provider>
  );
}

DataTable.Header = function Header({ columns }) {
  const { sort, onSortChange } = useContext(DataTableContext);
  return (
    <div className="data-table__header" role="row">
      {columns.map((c) => (
        <button
          key={c.key}
          type="button"
          className={`data-table__th data-table__th--${sort === c.key ? 'active' : 'idle'}`}
          onClick={() => onSortChange && onSortChange(c.key)}
        >
          {c.label}
        </button>
      ))}
    </div>
  );
};

DataTable.Body = function Body({ rows, render }) {
  return (
    <div className="data-table__body">
      {rows.map((row, i) => (
        <div key={row.id ?? i} className="data-table__row" role="row">
          {render(row)}
        </div>
      ))}
    </div>
  );
};

DataTable.Pagination = function Pagination({ page, totalPages, onChange }) {
  return (
    <nav className="data-table__pagination" aria-label="Pagination">
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        ‹
      </button>
      <span>{page + 1} / {totalPages}</span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        ›
      </button>
    </nav>
  );
};
