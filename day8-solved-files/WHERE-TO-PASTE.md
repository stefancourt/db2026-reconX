# Day 8 — Solved Files & How To Run

Day 8 is the second frontend day — a Vite + React SPA replacing the
Day-7 static dashboard. Zero backend Java changes; all your work is in
the `frontend/` folder.

**How this folder works**

The real `frontend/` tree ships with the components, hooks, contexts,
services, and tests scaffolded — most function bodies are just a
`// TODO(TICKET-…)` comment. This folder contains **complete drop-in
replacement files** — every TODO is filled in, every hook does what
its ticket says, the API service wires all endpoints, and the
`DataTable` test asserts real behaviour. You can:

- **Overlay** the whole `frontend/` subtree in one shot (fastest), or
- **Open each file** in this folder side-by-side with the starter to
  read the diff first, then copy the solved version over.

Both flows land at the same result. `node_modules/` and `dist/` are
not shipped — run `npm install` once after the overlay.

**What this folder ships** (a snapshot of the current `frontend/` tree,
excluding `node_modules/` and `dist/`):

- `frontend/index.html`, `vite.config.js`, `package.json`, `Dockerfile`, `nginx.conf`, `eslint.config.js`, `.dockerignore`
- `frontend/src/main.jsx`, `App.jsx`, `test-setup.js`, `styles/global.css`
- `frontend/src/context/` — `AuthContext.jsx`, `ThemeContext.jsx` (ADV124)
- `frontend/src/components/` — `DataTable.jsx` (ADV114), `withAuth.jsx` (ADV112), `withErrorBoundary.jsx` (ADV113), plus `__tests__/DataTable.test.jsx` (ADV125)
- `frontend/src/hooks/` — `useWebSocket.js` (ADV115), `useTradeStream.js` (ADV116), `useDebouncedSearch.js` (ADV117), `useInfiniteScroll.js` (ADV118)
- `frontend/src/pages/` — `Dashboard.jsx`, `Login.jsx`, `Trades.jsx`, `AddTrade.jsx` (ADV123 lives on AddTrade)
- `frontend/src/services/apiService.js`

## Quick start

```bash
# From the project root — one-shot overlay:
cp -R day8-solved-files/frontend/ frontend/
cd frontend && npm install    # first time only (node_modules is not in this folder)
```

---

## What Day 8 covers

Fifteen tickets (ADV111–125), all in `frontend/`:

| Ticket | Topic |
|---|---|
| ADV111 | Vite setup with path aliases |
| ADV112 | `withAuth(Component)` HOC |
| ADV113 | `withErrorBoundary(Component)` HOC |
| ADV114 | `<DataTable>` compound component |
| ADV115 | `useWebSocket(url, options)` hook |
| ADV116 | `useTradeStream()` hook (SSE) |
| ADV117 | `useDebouncedSearch(query, delay)` |
| ADV118 | `useInfiniteScroll(loadMore)` |
| ADV119 | `React.memo` on `<TradeRow />` |
| ADV120 | `useMemo` for portfolio value + P&L |
| ADV121 | `useCallback` on handlers passed to memoised children |
| ADV122 | `React.lazy` + `Suspense` route-based code splitting |
| ADV123 | Trade entry form (React Hook Form + Yup) |
| ADV124 | Theme context (light/dark) |
| ADV125 | React Testing Library tests for dashboard summary cards |

Nothing to overlay from this folder — you edit files under `frontend/`
directly.

---

## Run the project

Two terminals: backend + Vite dev server.

### Before you start

1. **Java 21** on the terminal that runs the backend: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
2. **Days 1–6 are applied** (backend must be at post-Day-6 state so JWT auth + SSE stream + REST endpoints all work):
   ```bash
   for d in day1 day2 day3 day4 day5 day6; do cp -R ${d}-solved-files/backend/ backend/; done
   ```
3. **Node 20+** available for Vite. `node --version`.

### Terminal 1 — backend

```bash
cd backend
./mvnw spring-boot:run       # port 8081, context path /api
```

### Terminal 2 — frontend

```bash
cd frontend
npm install                  # first time only
npm run dev                  # Vite serves on http://localhost:5173
```

Open <http://localhost:5173>, log in with a seeded user, and click
around. As you complete each ticket the corresponding page /
component / hook light up.

### Running the RTL tests (ADV125)

```bash
cd frontend
npm test
```

---

## What success looks like

- `npm run dev` boots without errors; the login page renders.
- A protected route redirects to `/login` when hit without a JWT (that's `withAuth` doing its job).
- Throwing inside a wrapped child renders your error-boundary fallback instead of blanking the page (`withErrorBoundary`).
- Typing quickly into the trade-search input fires one network request after you stop, not one per keystroke (`useDebouncedSearch`).
- Scrolling to the bottom of a paginated list auto-loads the next page (`useInfiniteScroll`).
- Toggling an unrelated piece of state does NOT re-render `<TradeRow />` in the React DevTools Profiler (`React.memo` + `useCallback`).
- `/`, `/trades`, `/trades/new`, `/login` each pull a distinct JS bundle in the Network tab (`React.lazy`).
- `npm test` prints all RTL tests green.

---

## Troubleshooting

- **CORS errors on `fetch`** — Vite dev server needs to proxy `/api/**` to `http://localhost:8081`. Add a `server.proxy` block in `vite.config.ts`.
- **SSE / WebSocket disconnects immediately** — your bearer token isn't being sent. `EventSource` doesn't support custom headers; pass the token as a query-param and have the backend accept either form.
- **`useEffect` fires twice in dev** — that's React 18 StrictMode intentionally double-invoking effects. Add a cleanup function so your effect is idempotent.
- **`useMemo` for portfolio value never recomputes** — you're missing a dependency in the deps array. Add every value the calc reads.
- **`React.lazy` throws "invalid element type"** — the imported chunk isn't a default export. `React.lazy(() => import('./X'))` needs `X` to `export default …`.
- **Port 5173 in use** — Vite picks the next free port automatically; check its startup output.

Second frontend day. Keep DevTools + React DevTools + Profiler open.
