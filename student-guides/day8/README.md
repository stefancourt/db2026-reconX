# Day 8 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day8/README.md](../../TrainersGuide/day8/README.md)
> **Module:** React Modules 2 & 3 — Advanced Patterns

## What you'll build today

Today is the day the ReconX user interface stops being a static dashboard
and turns into a real React application. You will set up a Vite project
with sensible path aliases, wrap pages in higher-order components for
auth and error handling, build a compound `<DataTable>` whose sub-pieces
share state through internal context, and pack a custom-hooks folder
with the four hooks that the rest of the app will lean on for the rest
of the week (`useWebSocket`, `useTradeStream`, `useDebouncedSearch`,
`useInfiniteScroll`). In the afternoon you will turn the screws on
performance — `React.memo`, `useMemo`, `useCallback`, `React.lazy` with
`Suspense` for route-level code splitting — add a `react-hook-form`
trade entry form with `yup` validation, wire a light/dark
`ThemeProvider`, and finish with React Testing Library coverage plus a
hands-on session with the React DevTools Profiler. By 17:00 the same
Vite dev server you boot at 10:00 should be a recognisable trader UI.

## Day at a glance

1. Standup and Day 7 unblock (React Module 1 leftovers)
2. AM concept block: React Module 2 (state, context, effects, optimisation, error boundaries)
3. **Workshop 8A** — Vite setup, HOCs, compound `<DataTable>` (TICKET-ADV111 – TICKET-ADV114)
4. Coffee
5. **Workshop 8B** — Custom hooks (TICKET-ADV115 – TICKET-ADV118)
6. Lunch
7. PM concept block: React Module 3 (HOC vs render props vs compound, code splitting)
8. **Workshop 8C** — Memoisation, code splitting, RHF + Yup, ThemeProvider (TICKET-ADV119 – TICKET-ADV124)
9. **Workshop 8D** — RTL test on the dashboard (TICKET-ADV125)
10. Buffer, unblock, or optional fast-finisher (TICKET-ADV127 — React DevTools Profiler walkthrough)
11. End-of-day debrief and Day 9 preview

## Exercises

There are 15 exercises today across four workshops, plus one optional
fast-finisher (TICKET-ADV127) for teams who finish early. The median
exercise lands around twenty minutes. Each one below ships with a
**Goal**, a concrete **Done when** checklist that tells you when to
stop, and three progressive hints. Open Hint 1 only after you have read
the goal and stared at the problem for a couple of minutes; open Hint 2
only after Hint 1 didn't unblock you; open Hint 3 only as a last resort
before asking your trainer. The hints get more specific as you go, but
none of them paste the solution — that's by design. The point is to
build the muscle memory, not the keystrokes.

Everything ships into `frontend/` (the Vite + React 19 project). Keep
the React Developer Tools extension open in your demo browser all day —
the Profiler tab will earn its keep this afternoon.

### Workshop 8A — Vite setup, HOCs, and the compound DataTable

This block gets the dev environment honest and walks you through the
two cross-cutting React patterns that everything else hangs off: HOCs
for guard-style wrapping (auth, error handling) and compound components
for parent-owned, child-composable UI (the trade table).

### TICKET-ADV111 — Vite setup with path aliases

**Goal:** Configure the Vite project so `@/components/...` style
imports resolve everywhere, with no `../../..` relatives in the tree.

**What**
- `frontend/vite.config.js` with a `resolve.alias` map (`@`, `@components`, `@hooks`, `@services`, `@context`, `@pages`) resolved via `fileURLToPath(new URL('./src/...', import.meta.url))`, plus `frontend/jsconfig.json` mirroring the same `paths` map.

**Why**
- Every page, hook, and test landing in ADV112–ADV125 imports from `@components/...` / `@hooks/...`; getting aliases wired once now keeps the day's 200+ import lines short and the IDE green.

**Observe**
- `npm run dev` logs `VITE vX.Y.Z ready` on `http://localhost:5173`; an `import { foo } from '@components/...'` resolves both at runtime and in the IDE (no red squiggles).

**Done when:**
- `npm run dev` boots the Vite server on `http://localhost:5173` within five seconds of a cold start.
- An import like `import { StatCard } from '@components/StatCard'` resolves at runtime *and* in the IDE (no red squiggles).
- At least five aliases are wired: `@`, `@components`, `@hooks`, `@services`, `@context` (plus `@pages` if you have it).

<details>
<summary>Hint 1 — gentle direction</summary>

Vite config is a JavaScript file, but it runs as an ES Module. That
changes which globals are available. Before you wire any aliases,
notice what the file already imports — and what the Node ESM equivalent
is for the things CommonJS used to give you for free.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The two file names that matter are `vite.config.js` (runtime aliases
for the bundler) and `jsconfig.json` (path resolution for the editor).
You need both — touching only the first leaves your IDE complaining
about every alias import. For the absolute path inside the Vite config,
look at `node:url`'s `fileURLToPath` and the `import.meta.url` value.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

In `vite.config.js`, the `resolve.alias` map's values should be the
result of `fileURLToPath` applied to a `new URL('./src/...', import.meta.url)`.
In `jsconfig.json`, set `compilerOptions.baseUrl` to `.` and add a
`paths` object whose keys mirror your alias names with `/*` suffixes.
Do not also leave a stray `baseUrl` in the Vite side — pick one
resolution mechanism per layer.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In `frontend/vite.config.js`, import `defineConfig` from `vite`, `react` from `@vitejs/plugin-react`, and `fileURLToPath`, `URL` from `node:url`.
2. Inside `defineConfig({...})`, add a `resolve.alias` map with six entries — `@`, `@components`, `@hooks`, `@services`, `@context`, `@pages` — each resolved with `fileURLToPath(new URL('./src/...', import.meta.url))`.
3. Add a `server` block with `port: 5173` and a `proxy` for `/api` and `/stream` to `http://localhost:8080`.
4. Add a `test` block (`environment: 'jsdom'`, `globals: true`, `setupFiles: './src/test/setup.js'`) so Vitest picks up DOM matchers later.
5. Create `frontend/jsconfig.json` with `compilerOptions.baseUrl: "."` and a `paths` map whose keys mirror the alias names with `/*` suffixes pointing at `src/*`.
6. Run `npm run dev` — the server should boot on `http://localhost:5173`; verify an alias import resolves both at runtime and in the IDE (no red squigglies).

**Reference solution** (`frontend/vite.config.js`):

```js
// Vite config + path aliases (@/components, @/hooks, ...)
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@':          fileURLToPath(new URL('./src',          import.meta.url)),
      '@components':fileURLToPath(new URL('./src/components',import.meta.url)),
      '@hooks':     fileURLToPath(new URL('./src/hooks',     import.meta.url)),
      '@services':  fileURLToPath(new URL('./src/services',  import.meta.url)),
      '@pages':     fileURLToPath(new URL('./src/pages',     import.meta.url)),
      '@context':   fileURLToPath(new URL('./src/context',   import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test-setup.js',
  },
});
```

</details>

**▶ Run the project — verify TICKET-ADV111 end-to-end**

Boot the Vite dev server and confirm aliases resolve in both runtime and the IDE.

```bash
cd frontend && npm install
npm run dev
```

**Observe:**

- Terminal logs `VITE vX.Y.Z  ready in <N>ms` and `Local: http://localhost:5173/`.
- Opening `http://localhost:5173` loads the app shell without console errors.
- Failure signal: `Failed to resolve import "@components/..."` means an alias is wired in `vite.config.js` but missing from `jsconfig.json` (or vice versa).

---

### TICKET-ADV112 — HOC: `withAuth(Component)`

**Goal:** Build a higher-order component that wraps any page and
redirects unauthenticated users to `/login` before the wrapped
component ever renders.

**What**
- `frontend/src/components/withAuth.jsx` exporting a `withAuth(Component)` factory that reads `useAuth()`, returns `<Navigate to="/login" replace state={{ from: location.pathname }} />` when no user is present, and sets `WithAuth.displayName` for DevTools.

**Why**
- Day 7 stood up `AuthContext` + JWT; this HOC is the gate every protected page in ADV123/ADV125 sits behind, and the pattern repeats on Day 9 once role-based authorisation lands.

**Observe**
- Clearing `localStorage.jwt` and visiting `/dashboard` flips the URL to `/login` before the protected page paints; React DevTools shows `withAuth(Dashboard)` (not `WithAuth`) in the component tree.

**Done when:**
- An export like `export default withAuth(Dashboard)` redirects to `/login` when no user is in `AuthContext`.
- The wrapped component is never visible — not even for a single frame — when the user is logged out.
- The React DevTools component tree shows a useful display name (not just `<WithAuth>` for every wrapped page).

<details>
<summary>Hint 1 — gentle direction</summary>

Think about *when* the redirect should happen. If you wait until after
the first paint, the user sees the protected page flash for a moment
before being kicked out. What can you do during render itself that
short-circuits the wrapped component?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`react-router-dom` ships a component you can return from render to
trigger navigation without an effect — look in the router exports for
the one that takes a `to` prop. Also revisit the `useAuth` hook from
the AuthContext you set up for Day 7 (or stub it now) — it needs to
expose both the current user and a loading flag.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The HOC is a function that takes `Component` and returns a new
function component. Inside that new component, read auth state; if
loading, render a spinner; if no user, return the router's redirect
component with `replace` set and ideally a `state` carrying the
attempted path; otherwise render the wrapped component with `{...props}`
forwarded. Set `displayName` on the returned function to wrap the
inner component's name — this is the line junior HOCs forget.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/hocs/withAuth.jsx`; import `Navigate` and `useLocation` from `react-router-dom`, and `useAuth` from `@context/AuthContext`.
2. Export a `withAuth(Component)` factory that defines and returns an inner `WithAuth(props)` function component.
3. Inside `WithAuth`, call `useAuth()` to read `{ user, isLoading }` and `useLocation()` for the attempted path.
4. If `isLoading`, return a spinner placeholder; if `!user`, return `<Navigate to="/login" replace state={{ from: location.pathname }} />`.
5. Otherwise return `<Component {...props} />`.
6. Set `WithAuth.displayName = \`withAuth(${Component.displayName || Component.name || 'Component'})\`` so DevTools shows a useful name.
7. In `Dashboard.jsx`, change the default export to `export default withAuth(Dashboard)` and verify a logged-out visit redirects immediately to `/login`.

**Reference solution** (`frontend/src/components/withAuth.jsx`):

```jsx
// withAuth HOC: redirects to /login if no JWT.
import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@context/AuthContext.jsx';

export function withAuth(Component) {
  function WithAuth(props) {
    const { user } = useAuth();
    if (!user) return <Navigate to="/login" replace />;
    return <Component {...props} />;
  }
  WithAuth.displayName = `withAuth(${Component.displayName || Component.name || 'Component'})`;
  return WithAuth;
}
```

</details>

**▶ Run the project — verify TICKET-ADV112 end-to-end**

Dev server already running; clear any stored JWT and visit a `withAuth`-wrapped route.

```bash
# In the browser DevTools console:
localStorage.removeItem('jwt')
# then navigate to a protected route, e.g. /dashboard
```

**Observe:**

- URL flips to `/login` immediately; the protected page never paints.
- After logging in, navigating back to the original route renders normally.
- React DevTools tree shows `withAuth(Dashboard)` as the display name (not `WithAuth`).

---

### TICKET-ADV113 — HOC: `withErrorBoundary(Component)`

**Goal:** Wrap any component in an ErrorBoundary that catches render
errors and shows a recoverable fallback, exposed as an HOC so any page
can opt in.

**What**
- `frontend/src/components/withErrorBoundary.jsx` containing a class `ErrorBoundary` with `getDerivedStateFromError` + `componentDidCatch` + a `Try again` reset, plus a `withErrorBoundary(Component)` factory composed outermost around `withAuth`.

**Why**
- Day 9's RTL/integration tests and the Day 10 demo cannot afford a single runtime throw to white-screen the app; an opt-in boundary keeps the rest of the SPA alive when one page's render explodes.

**Observe**
- Throwing inside a wrapped child renders a `role="alert"` fallback with `Something went wrong`; console logs `ErrorBoundary caught Error: ...` from `componentDidCatch`, and clicking `Try again` remounts the children.

**Done when:**
- Throwing an error inside a wrapped component shows a fallback UI, not React's red screen.
- The fallback has a "Try again" affordance that resets the boundary's state and re-renders the children.
- A second error after reset is caught again (the boundary is reusable, not single-shot).

<details>
<summary>Hint 1 — gentle direction</summary>

Error boundaries are one of the very few places in modern React where
you still have to write a class. There's no hook equivalent in React
18. Which two lifecycle methods does React reserve specifically for
catching render errors?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look up `getDerivedStateFromError` (static, returns the next state)
and `componentDidCatch` (instance, for side-effects like logging).
Your boundary needs internal state with at least a `hasError` flag and
the captured error. The render method switches on that flag. Then the
HOC wrapper is the small bit on top — a function that returns
`<ErrorBoundary><Component {...props} /></ErrorBoundary>`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The class has state shaped like `{ hasError, error }`. The static
method returns the next state from the captured error. The instance
method takes `(error, info)` and logs them. Render either returns
`this.props.children` when fine, or the fallback (a default JSX block
or a `Fallback` component received via props). Expose a reset handler
that flips `hasError` back to false. The HOC wrapper around the class
is the same display-name shape as `withAuth`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/hocs/withErrorBoundary.jsx`; import `React`.
2. Declare a class `ErrorBoundary extends React.Component` with `state = { hasError: false, error: null }`.
3. Add `static getDerivedStateFromError(error)` that returns `{ hasError: true, error }`, and `componentDidCatch(error, info)` that logs.
4. Add a `handleReset` instance method that calls `setState({ hasError: false, error: null })`.
5. In `render()`, branch on `state.hasError`: if a `Fallback` prop was provided, render it with `error` and `onReset`; otherwise render a default `role="alert"` block with a "Try again" button wired to `handleReset`.
6. Export a `withErrorBoundary(Component, Fallback)` factory that returns an inner component wrapping `<Component {...props} />` in `<ErrorBoundary fallback={Fallback}>`.
7. Set `WithErrorBoundary.displayName` for DevTools, then compose with `withAuth` (boundary outermost) on a page to verify a thrown error renders the fallback and reset re-renders children.

**Reference solution** (`frontend/src/components/withErrorBoundary.jsx`):

```jsx
// withErrorBoundary HOC: wraps a component in an error boundary.
import React from 'react';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) { return { error }; }

  componentDidCatch(error, info) {
    // In real prod we'd ship this to Sentry / browser-side logger.
    // eslint-disable-next-line no-console
    console.error('ErrorBoundary caught', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div role="alert" className="error-fallback">
          <h2>Something went wrong</h2>
          <pre>{String(this.state.error.message || this.state.error)}</pre>
          <button onClick={() => this.setState({ error: null })}>Try again</button>
        </div>
      );
    }
    return this.props.children;
  }
}

export function withErrorBoundary(Component) {
  function WithErrorBoundary(props) {
    return (
      <ErrorBoundary>
        <Component {...props} />
      </ErrorBoundary>
    );
  }
  WithErrorBoundary.displayName = `withErrorBoundary(${Component.displayName || Component.name || 'Component'})`;
  return WithErrorBoundary;
}
```

</details>

**▶ Run the project — verify TICKET-ADV113 end-to-end**

Force a render-time error inside a `withErrorBoundary`-wrapped child and watch the fallback take over.

```bash
# In a wrapped child component, temporarily add:  throw new Error('boom');
npm run dev
```

**Observe:**

- The fallback UI (`role="alert"` with "Try again") renders instead of a white screen.
- Browser console logs `ErrorBoundary caught Error: boom ...` from `componentDidCatch`.
- Clicking "Try again" remounts the children; a second thrown error is caught again (boundary is reusable, not single-shot).

---

### TICKET-ADV114 — Compound Component: `<DataTable>`

**Goal:** Build a `<DataTable>` whose `Header`, `Body`, and `Pagination`
sub-components share sort state and pagination via internal context,
so callers compose them as children rather than as flat props.

**What**
- `frontend/src/components/DataTable.jsx` exporting a root `<DataTable data>` that owns sort + page state via internal `DataTableContext`, with `DataTable.Header`, `DataTable.Body`, and `DataTable.Pagination` sub-components attached as static properties.

**Why**
- The trades list, breaks queue, and audit log in later days all want the same sortable/paginated shape; one compound component beats three bespoke tables and beats a flat-props table that needs 14 props per call site.

**Observe**
- Clicking a `<DataTable.Header>` cell flips sort direction and reorders the body; omitting `<DataTable.Pagination />` still renders a working table — the sub-components are independently optional.

**Done when:**
- Calls like `<DataTable.Header columns={...} />`, `<DataTable.Body renderRow={...} />`, and `<DataTable.Pagination />` work when nested inside `<DataTable data={...}>`.
- Clicking a header cell sorts the body and toggles ascending/descending; pagination respects the current sort.
- Omitting `<DataTable.Pagination />` still leaves a working table — sub-components are optional.

<details>
<summary>Hint 1 — gentle direction</summary>

Look at how the browser's native `<select>` shares state with its
`<option>` children — you don't pass `value` down to every option. The
parent owns the state; children read it implicitly. That's the same
shape you need here.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reach for `createContext` scoped *inside* this module (not exported);
the parent component wraps `{children}` in the provider, and each
sub-component reads from it with `useContext`. Sub-components get
attached to the parent function as static properties
(`DataTable.Header = Header`) so the dot-syntax works. The shared
value should expose at minimum the current rows, page state, and the
sort key/direction setters.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The provider's value is an object built with `useMemo` (so context
consumers don't re-render unnecessarily) containing the paged rows,
the total row count, page index, page size, the page setter, the
sort key, sort direction, and their setters. The `Header` reads
sort state and writes it back on click; `Body` reads `rows` and maps
them through a `renderRow` prop; `Pagination` reads page state and
calls `setPage`. Throw a clear error from a small `useDataTable` hook
when context is null — surfaces "used outside `<DataTable>`" misuse early.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/components/DataTable/DataTable.jsx` and import `createContext`, `useContext`, `useMemo`, `useState`.
2. Declare a module-scoped `DataTableContext = createContext(null)` and a small `useDataTable()` hook that throws if context is null.
3. Export `DataTable({ data, pageSize = 10, children })` which owns `page`, `sortKey`, `sortDir` state via `useState`.
4. Derive `sorted` and `paged` arrays via two `useMemo` blocks keyed on the relevant deps.
5. Build the provider `value` with `useMemo({ rows: paged, totalRows, page, pageSize, setPage, sortKey, sortDir, setSortKey, setSortDir })`.
6. Define `Header({ columns })` (reads sort state, toggles on click), `Body({ renderRow })` (maps `rows`), and `Pagination()` (Prev/Next buttons).
7. Attach `DataTable.Header = Header; DataTable.Body = Body; DataTable.Pagination = Pagination;` so dot-syntax composition works.
8. Use it from a page with `<DataTable data={trades}>…</DataTable>` and verify clicking headers sorts and omitting `<DataTable.Pagination />` still works.

**Reference solution** (`frontend/src/components/DataTable.jsx`):

```jsx
// Compound <DataTable> with Header / Body / Pagination subcomponents.
import React, { createContext, useContext } from 'react';

const DataTableContext = createContext({ sort: null, page: 0, size: 20 });

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
      <button disabled={page === 0} onClick={() => onChange(page - 1)}>‹</button>
      <span>{page + 1} / {totalPages}</span>
      <button disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>›</button>
    </nav>
  );
};
```

</details>

**▶ Run the project — verify TICKET-ADV114 end-to-end**

Render the compound `<DataTable>` on a trades page and exercise sort + pagination.

```bash
npm run dev
# navigate to the page using <DataTable data={trades}>...</DataTable>
```

**Observe:**

- Clicking a header cell reorders the body rows; clicking again flips ascending/descending.
- The `aria-sort` (or active-class) on the clicked column flips to match the direction.
- Removing `<DataTable.Pagination />` still leaves a working table — sub-components are optional.
- Failure signal: a console error from `useDataTable()` ("used outside `<DataTable>`") means a sub-component is rendered outside the provider.

---

### Workshop 8B — Custom hooks

This is the meatiest block of the day. Four hooks, all small, all easy
to get wrong in subtle dependency-array ways. Open the Network tab in
DevTools and keep it open — it will catch the runaway socket bug
before you notice it in the UI.

### TICKET-ADV115 — `useWebSocket(url, options)`

**Goal:** A custom hook that opens a WebSocket connection, exposes
`{ data, status, send }`, and reconnects with exponential backoff on
unexpected close.

**What**
- `frontend/src/hooks/useWebSocket.js` exposing `{ data, status, send }`, holding the socket + retry counter + reconnect timer in `useRef`s, and reconnecting on unexpected close with `min(maxDelay, baseDelay * 2 ** retries)` backoff.

**Why**
- ADV116's `useTradeStream` and Day 9's live break-feed both need the same connection lifecycle; isolating it here means one place to fix when the backoff curve or the cap changes.

**Observe**
- DevTools Network -> WS tab shows exactly one socket on mount and zero after unmount; killing the backend triggers reconnects at ~500ms / 1s / 2s before `maxRetries` caps the loop.

**Done when:**
- Mounting a consumer opens exactly one WebSocket; unmounting closes it; the Network tab agrees.
- A forced server-side close triggers a reconnect after a growing delay (1s, 2s, 4s, ...), capped by a max delay and a max retry count.
- The `send` function is a no-op when the socket isn't `OPEN`, and the returned `data` updates on each parsed message.

<details>
<summary>Hint 1 — gentle direction</summary>

If you put `new WebSocket(url)` directly into a `useEffect` and list
something unstable in the deps array, you will open dozens of sockets
per second. Open the Network tab and watch the count. The fix isn't
removing the effect — it's being honest about what really changes.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The socket instance itself must live in a `useRef`, not in state and
not as a dep. Same for the retry counter and the reconnect timer
handle. The only dep that legitimately drives a fresh connection is
the `url`. For the backoff, the formula you want is along the lines of
`min(maxDelay, baseDelay * 2 ** retries)`. Remember to clean up the
timer *and* close the socket in the effect's return.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Hooks: `useState` for `data` and `status`, three `useRef`s (socket,
retries, reconnect timer), plus a `useRef` flag for "we are unmounting,
do not reconnect." A `connect` function (wrapped in `useCallback`)
attaches `onopen`, `onmessage`, `onerror`, `onclose` handlers; on close
it schedules `connect` again via `setTimeout` *only* if `shouldStop` is
false and retries are under the cap. The single `useEffect` calls
`connect` once on mount and cleans up on unmount. The `send` callback
checks `wsRef.current.readyState === WebSocket.OPEN` before pushing.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/hooks/useWebSocket.js`; import `useEffect`, `useRef`, `useState`, `useCallback`.
2. Declare a `DEFAULTS` constant for `reconnect`, `maxRetries`, `baseDelay`, `maxDelay`; merge with caller `options`.
3. Add `useState` for `data` and `status`, plus `useRef`s for `wsRef`, `retriesRef`, `timerRef`, and a `shouldStop` flag.
4. Define a `connect` function (wrapped in `useCallback`) that opens a new `WebSocket(url)`, wires `onopen`/`onmessage`/`onerror`/`onclose`, and on close re-schedules via `setTimeout(connect, min(maxDelay, baseDelay * 2 ** retries))` unless `shouldStop` or `retries >= maxRetries`.
5. Use a single `useEffect([connect])` that calls `connect()` on mount and in cleanup sets `shouldStop = true`, clears `timerRef`, and closes the socket if `readyState <= 1`.
6. Define `send` as a `useCallback` that no-ops unless `wsRef.current.readyState === WebSocket.OPEN`; stringify non-string payloads.
7. Return `{ data, status, send }`; consume the hook from a component and watch the Network tab to confirm exactly one socket.

**Reference solution** (`frontend/src/hooks/useWebSocket.js`):

```js
// useWebSocket(url) with auto-reconnect (exp backoff up to 5 tries).
import { useEffect, useRef, useState } from 'react';

export function useWebSocket(url, { reconnect = true, maxRetries = 5 } = {}) {
  const [data, setData] = useState(null);
  const [status, setStatus] = useState('connecting');
  const wsRef = useRef(null);
  const retries = useRef(0);

  useEffect(() => {
    let cancelled = false;
    function connect() {
      const ws = new WebSocket(url);
      wsRef.current = ws;
      ws.onopen    = () => { if (!cancelled) { setStatus('open'); retries.current = 0; } };
      ws.onmessage = (e) => { if (!cancelled) { try { setData(JSON.parse(e.data)); } catch { setData(e.data); } } };
      ws.onerror   = () => { if (!cancelled) setStatus('error'); };
      ws.onclose   = () => {
        if (cancelled) return;
        setStatus('closed');
        if (reconnect && retries.current < maxRetries) {
          const delay = Math.min(30000, 500 * 2 ** retries.current++);
          setTimeout(connect, delay);
        }
      };
    }
    connect();
    return () => {
      cancelled = true;
      wsRef.current && wsRef.current.close();
    };
  }, [url, reconnect, maxRetries]);

  const send = (payload) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(typeof payload === 'string' ? payload : JSON.stringify(payload));
    }
  };

  return { data, status, send };
}
```

</details>

**▶ Run the project — verify TICKET-ADV115 end-to-end**

Backend must be running so the WebSocket endpoint exists; then mount a `useWebSocket` consumer.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
```

**Observe:**

- DevTools Network → WS tab shows exactly one socket open on mount; status flips to `open`.
- Navigating away (unmount) closes the socket — no growing connection count.
- Forcing the backend to drop the socket triggers a reconnect after ~500ms, then ~1s, then ~2s (exponential backoff), capped by `maxRetries`.
- Failure signal: dozens of sockets opening per second means the deps array or refs are wrong — close the tab before your laptop melts.

---

### TICKET-ADV116 — `useTradeStream()` with SSE

**Goal:** Subscribe to the existing Day 7 server-sent events endpoint
and return `{ trades, isConnected }`, accumulating events into a
bounded local buffer.

**What**
- `frontend/src/hooks/useTradeStream.js` opening an `EventSource` against `/stream/trades`, returning `{ trades, isConnected }`, and bounding the buffer to ~200 entries via an immutable `[next, ...prev].slice(0, 200)` updater.

**Why**
- Day 7 stood up the SSE endpoint; this hook is what the dashboard summary cards (ADV120) and the live trade table actually consume, and Day 9's break-resolution UI reuses the same buffer-cap pattern.

**Observe**
- Network -> EventStream shows one open connection; new trade events appear at index 0 within ~1s of backend emission, and the array length never climbs past the cap even under sustained load.

**Done when:**
- Mounting a consumer opens an EventSource against `/stream/trades`; unmounting closes it.
- New trades appear at the top of the returned array within ~1 second of being emitted by the backend.
- The buffer never exceeds a fixed cap (around 200) — the oldest entries fall off as new ones arrive.

<details>
<summary>Hint 1 — gentle direction</summary>

Do not reach for `fetch` and a `ReadableStream`. The browser ships a
single-purpose primitive that already handles SSE framing,
reconnection, and event types. Find it before you write any code.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The class is `EventSource`. It exposes `onopen`, `onerror`, `onmessage`
plus a generic `addEventListener` for *named* events (the backend may
send `event: trade-matched` lines, in which case `onmessage` won't fire
for those). For React state, never mutate the array — every update
must be a brand new array reference, otherwise React skips the
re-render. The bounded cap lives in the same updater.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Single `useEffect` keyed on the endpoint; instantiate `EventSource`,
wire the three default handlers, then `addEventListener` for any
named events you expect (`trade-matched` is the obvious one for
status updates). The functional updater pattern is
`setTrades(prev => [next, ...prev].slice(0, MAX_BUFFER))` — note the
spread (no push) and the slice. Cleanup closes the source.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/hooks/useTradeStream.js`; import `useEffect`, `useState`.
2. Declare `MAX_BUFFER = 200` and export `useTradeStream(endpoint = '/stream/trades')`.
3. Add `useState` for `trades` (init `[]`) and `isConnected` (init `false`).
4. Inside `useEffect([endpoint])`, instantiate `new EventSource(endpoint)`; wire `onopen` to set connected true, `onerror` to set it false.
5. In `onmessage`, parse JSON and call `setTrades(prev => [trade, ...prev].slice(0, MAX_BUFFER))` — never mutate.
6. Add `addEventListener('trade-matched', ...)` for named events that flip a trade's status in place.
7. Return `() => es.close()` from the effect so unmount tears the connection down; return `{ trades, isConnected }` from the hook.

**Reference solution** (`frontend/src/hooks/useTradeStream.js`):

```js
// useTradeStream() — SSE subscription returning live trades.
import { useEffect, useState } from 'react';

export function useTradeStream(url = '/api/v1/trades/stream') {
  const [trades, setTrades] = useState([]);
  const [isConnected, setConnected] = useState(false);

  useEffect(() => {
    const sse = new EventSource(url);
    sse.onopen = () => setConnected(true);
    sse.onmessage = (e) => {
      try {
        const t = JSON.parse(e.data);
        setTrades((prev) => [t, ...prev].slice(0, 200));
      } catch { /* ignore malformed payload */ }
    };
    sse.onerror = () => setConnected(false);
    return () => sse.close();
  }, [url]);

  return { trades, isConnected };
}
```

</details>

**▶ Run the project — verify TICKET-ADV116 end-to-end**

Backend must be emitting SSE on `/api/v1/trades/stream`; then mount the Dashboard.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
```

**Observe:**

- DevTools Network → EventStream tab shows an open `EventSource` against `/api/v1/trades/stream` with `text/event-stream` content-type.
- New trades stream in and prepend to the array; `isConnected` flips to `true` after `onopen`.
- The buffer never exceeds ~200 entries — oldest trades fall off as new ones arrive.
- Failure signal: connection thrash (open/close in a loop) means the effect deps are unstable.

---

### TICKET-ADV117 — `useDebouncedSearch(query, delay)`

**Goal:** A hook that takes a raw query string and a delay, and
returns a debounced version that only updates once typing has paused
for `delay` ms.

**What**
- `frontend/src/hooks/useDebouncedSearch.js` with one `useState` + one `useEffect([query, delay])` whose cleanup calls `clearTimeout` so only the trailing keystroke survives the debounce window.

**Why**
- The search box on the trades page and the instrument lookup on `/trades/new` (ADV123) both fire API calls per character without this; folding the debounce into a hook makes ADV121's `useCallback` patterns cleaner downstream.

**Observe**
- Typing `AAPL` quickly fires exactly one `/api/trades?q=AAPL` request ~300ms after the last keystroke (Network panel), not one per character.

**Done when:**
- Rapid keystrokes produce only *one* update to the debounced value after the user pauses.
- Changing the delay parameter at runtime changes the debounce window without leaking the old timer.
- The hook is a stable building block — replacing every place that has an inline `setTimeout`/`clearTimeout` debounce with one call to this hook produces the same behaviour.

<details>
<summary>Hint 1 — gentle direction</summary>

Effects return a cleanup function. React calls that cleanup just before
the next time it runs the effect (and once at unmount). What does that
mean if you schedule a `setTimeout` inside the effect and the deps
change mid-flight?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

You only need one `useState` (for the debounced value) and one
`useEffect` whose deps are `[query, delay]`. Inside the effect you
`setTimeout` to write the latest query into the debounced state. The
return value of the effect calls `clearTimeout` on the handle you just
created. That's the whole hook.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Initial state mirrors the incoming `query` (so the very first render
has a usable value). Effect: schedule a timer; on cleanup, cancel it.
Return the debounced value. No refs, no extra state — if you find
yourself adding either, you've probably let the timer survive a re-run.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/hooks/useDebouncedSearch.js`; import `useEffect`, `useState`.
2. Export `useDebouncedSearch(query, delay = 300)`.
3. Initialise `[debounced, setDebounced]` from the incoming `query` so render 1 has a usable value.
4. Inside `useEffect([query, delay])`, schedule `setTimeout(() => setDebounced(query), delay)` and capture the timer handle.
5. Return `() => clearTimeout(timer)` from the effect so superseded keystrokes are cancelled.
6. Return `debounced`; verify in a consumer that rapid typing produces exactly one update after the pause.

**Reference solution** (`frontend/src/hooks/useDebouncedSearch.js`):

```js
// useDebouncedSearch(query, delay).
import { useEffect, useState } from 'react';

export function useDebouncedSearch(query, delay = 300) {
  const [debounced, setDebounced] = useState(query);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(query), delay);
    return () => clearTimeout(id);
  }, [query, delay]);
  return debounced;
}
```

</details>

**▶ Run the project — verify TICKET-ADV117 end-to-end**

Mount a search input that calls the hook, then type rapidly while watching the Network tab.

```bash
cd frontend && npm run dev
# focus the search input and type "AAPL" quickly, then pause
```

**Observe:**

- Exactly one API call fires ~300ms after the last keystroke — not one per character.
- Changing `delay` at runtime adjusts the window without leaking the previous timer.
- Failure signal: a request per keystroke means the cleanup `clearTimeout` is missing or the effect deps are wrong.

---

### TICKET-ADV118 — `useInfiniteScroll(loadMore)`

**Goal:** A hook that returns a `sentinelRef`; attaching it to a
sentinel element at the bottom of a list calls `loadMore()` whenever
that sentinel scrolls into view.

**What**
- `frontend/src/hooks/useInfiniteScroll.js` returning a `sentinelRef`; an `IntersectionObserver` created once observes the sentinel, and a `useRef` mirrors the latest `loadMore` so the callback dodges stale closures without recreating the observer.

**Why**
- The trades list grows past 500 rows quickly; pairing this hook with ADV119's memoised rows keeps scrolling smooth, and Day 9's audit log reuses the same sentinel pattern.

**Observe**
- Scrolling near the bottom invokes `loadMore` exactly once per intersection (console-log inside the callback); unmounting the consumer disconnects the observer with no `IntersectionObserver` leak in Memory tab snapshots.

**Done when:**
- Scrolling near the bottom triggers `loadMore` exactly once per entry, not on every scroll event.
- `loadMore` callbacks are picked up on each render (no stale closures over old state) while the observer itself is created once.
- Unmounting the consumer disconnects the observer — no memory leak across page navigations.

<details>
<summary>Hint 1 — gentle direction</summary>

Subscribing to `window.onscroll` and computing offsets by hand is the
2014 way. The browser has a single-purpose API for "is this element
visible right now?" that runs off the main thread. Find it.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`IntersectionObserver` is what you want. You'll need two refs: one for
the sentinel DOM node (returned from the hook), and one to hold the
latest `loadMore` callback so the observer doesn't have to be recreated
every time the consumer's callback changes identity. Guard the observer
callback on `entry.isIntersecting` so leaving the viewport doesn't
re-trigger it.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

One `useEffect` updates the callback ref whenever the incoming
`loadMore` changes. A separate `useEffect` (deps include only stable
options like `rootMargin`) reads the sentinel ref's current node, sets
up the observer, and registers cleanup that calls `observer.disconnect()`.
The observer's callback destructures the first entry, checks
`isIntersecting`, and invokes `loadMoreRef.current()`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/hooks/useInfiniteScroll.js`; import `useEffect`, `useRef`.
2. Export `useInfiniteScroll(loadMore, { rootMargin = '200px' } = {})`.
3. Declare `sentinelRef = useRef(null)` (returned to the caller) and `loadMoreRef = useRef(loadMore)`.
4. Add a `useEffect([loadMore])` that writes the latest `loadMore` into `loadMoreRef.current` — no observer churn.
5. Add a separate `useEffect([rootMargin])` that reads `sentinelRef.current`, returns early if null, instantiates an `IntersectionObserver` guarded on `entry.isIntersecting`, and calls `loadMoreRef.current()`.
6. Return `() => observer.disconnect()` from that effect; return `sentinelRef` from the hook.
7. Attach the ref to a 1px-tall sentinel `<div ref={sentinelRef} />` below the list and verify scrolling triggers exactly one `loadMore` per entry.

**Reference solution** (`frontend/src/hooks/useInfiniteScroll.js`):

```js
// useInfiniteScroll: invokes loadMore() when sentinel is visible.
import { useEffect, useRef } from 'react';

export function useInfiniteScroll(loadMore) {
  const sentinelRef = useRef(null);
  useEffect(() => {
    if (!sentinelRef.current) return undefined;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) loadMore();
    }, { threshold: 0.1 });
    observer.observe(sentinelRef.current);
    return () => observer.disconnect();
  }, [loadMore]);
  return sentinelRef;
}
```

</details>

**▶ Run the project — verify TICKET-ADV118 end-to-end**

Render a paginated list that uses the hook, then scroll to the bottom.

```bash
cd frontend && npm run dev
# scroll a paginated list down until the sentinel enters the viewport
```

**Observe:**

- A new page fetches automatically when the sentinel intersects the viewport — exactly once per page, not on every scroll event.
- A loading spinner is visible during each fetch; new rows append to the list.
- Navigating away tears the observer down (no memory leak across page changes).
- Failure signal: `loadMore` firing repeatedly while the sentinel is visible means the `isIntersecting` guard is missing.

---

### Workshop 8C — Memoisation, code splitting, forms, and theme

The afternoon turns the screws. You'll find out the hard way that
`React.memo` without `useCallback` is half a fix, that `useMemo` on a
sum is theatre, that `React.lazy` without `Suspense` throws, and that
`react-hook-form` only rewards you when you stop fighting it.

### TICKET-ADV119 — `React.memo` on `<TradeRow />`

**Goal:** Wrap the trade row component in `React.memo` with a custom
equality check so that rows only re-render when fields they actually
display change.

**What**
- `frontend/src/components/TradeRow.jsx` exporting `React.memo(TradeRowImpl, areEqual)` where `areEqual` compares only the rendered fields (`id`, `status`, `price`) plus `prev.onClick === next.onClick`.

**Why**
- The trades page renders hundreds of rows on every SSE push; without this memo every unrelated state flip (filter dropdown, side panel) re-renders the whole list. ADV121 then closes the `onClick` identity gap so the memo actually holds.

**Observe**
- React DevTools Profiler with "Record why each component rendered" on: an unrelated parent update produces a commit chart with zero `<TradeRow>` entries; mutating one trade's `status` re-renders only that row.

**Done when:**
- Updating an unrelated piece of dashboard state does not re-render existing trade rows (verify in the Profiler).
- Mutating a trade's `status` or `price` re-renders only that row.
- The equality function compares the precise fields the row actually reads — not `JSON.stringify(prev) === JSON.stringify(next)` shortcuts.

<details>
<summary>Hint 1 — gentle direction</summary>

`React.memo` defaults to shallow equality across props. That's
sometimes too strict (if a parent makes a new object every render the
memo never hits) and sometimes too loose (the row only reads three
fields, but shallow looks at the whole `trade` object reference). Which
is your case?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`React.memo`'s second argument is an equality predicate. Sign matters:
returning `true` means "props are equal, skip the render"; returning
`false` means "render". Compare only the fields the row reads
(`id`, `status`, `price` — whatever your JSX touches) plus any
handler props by identity.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Extract the row's implementation into a regular function component,
then `export const TradeRow = React.memo(Impl, areEqual)`. The
`areEqual` function reads each comparison-relevant field from both
`prev.trade` and `next.trade` and ANDs them all together. Add identity
comparisons for any callback props (`prev.onClick === next.onClick`)
— those will fail until TICKET-ADV121 fixes them.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/components/TradeRow.jsx`; import `React`.
2. Define an internal `TradeRowImpl({ trade, onClick })` that renders a `<tr>` with the fields the row actually displays (ref, instrument, qty, price, status pill).
3. Write an `areEqual(prev, next)` predicate that returns `true` only when every rendered field on `prev.trade` matches `next.trade` (`id`, `status`, `price`) and `prev.onClick === next.onClick`.
4. Export `export const TradeRow = React.memo(TradeRowImpl, areEqual)`.
5. Render a list of `<TradeRow />` and trigger an unrelated parent state update; confirm in the Profiler that rows do **not** re-render.
6. Note that the `onClick` identity check will fail until TICKET-ADV121 wraps the handler in `useCallback`.

**Reference solution** (`frontend/src/components/TradeRow.jsx`):

```jsx
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
```

</details>

**▶ Run the project — verify TICKET-ADV119 end-to-end**

Render a Trades page populated with `<TradeRow />` and trigger an unrelated parent state change while watching the Profiler.

```bash
cd frontend && npm run dev
# open React DevTools → Profiler, record, toggle some unrelated state (e.g. a filter dropdown), stop
```

**Observe:**

- Existing `<TradeRow>` instances do **not** appear in the commit chart for the unrelated update — `areEqual` returned true.
- Mutating a single trade's `status` or `price` re-renders only that row.
- Until TICKET-ADV121 wraps the parent `onClick` in `useCallback`, the row will still re-render with reason `props changed: onClick` — that is the expected pre-fix state.

---

### TICKET-ADV120 — `useMemo` for portfolio value and P&L

**Goal:** Wrap genuinely expensive aggregations (portfolio value,
matched/unmatched/disputed counts and totals) in `useMemo` so they
recompute only when the trades array actually changes.

**What**
- `useMemo`-wrapped portfolio value, P&L, and matched/unmatched/disputed counts inside `Dashboard.jsx`, each with `[trades]` as the sole dep so unrelated state flips skip the reduce.

**Why**
- ADV116's bounded buffer streams new trades constantly; recomputing four aggregations on every unrelated render (theme toggle, side panel) wastes the budget the Profiler will measure in ADV127.

**Observe**
- Adding a `console.log('recomputing portfolio')` inside the `useMemo` factory: the log fires once per trade arrival and zero times when an unrelated piece of state changes (panel open/close, theme toggle).

**Done when:**
- Re-renders triggered by *non-trade* state changes (e.g. opening a side panel) do not recompute the aggregations.
- A new trade arriving on the SSE stream recomputes them once.
- You can articulate *why* each memoised value is worth caching (cost vs cache overhead) for every `useMemo` you add.

<details>
<summary>Hint 1 — gentle direction</summary>

`useMemo` is a *hint*, not a guarantee — React reserves the right to
throw the cache away. Don't reach for it on `a + b`. The rule of thumb
is: memoise when the calc is meaningfully expensive, or when the
*identity* of the result feeds into another memo downstream.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

A `console.time` / `console.timeEnd` around the unwrapped calculation
tells you whether it's worth caching. The deps array for both
aggregations should be just `[trades]` — if you find yourself adding
unstable objects there, you've moved a side-effect inside the memo.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Two `useMemo` calls. The first returns a single number (portfolio
value) from `trades.reduce(...)`. The second returns an object literal
with `matchedCount`, `unmatchedCount`, `disputedCount`, and any
totals you display, built by filtering trades by status. Both are
keyed on `[trades]`. Hand the results down to `<StatCard>` components.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `frontend/src/pages/Dashboard.jsx` and import `useMemo` from `react`.
2. Replace the inline `trades.reduce(...)` portfolio sum with a `useMemo(() => trades.reduce(...), [trades])`.
3. Add a second `useMemo` that filters `trades` by status into `matched`, `unmatched`, `disputed` arrays and returns an object literal with counts and matched value.
4. Pass each derived value down to a `<StatCard>`.
5. Verify in the Profiler that opening an unrelated side panel does not re-execute either memo.
6. Articulate (in code review) why each memoised value is worth caching — meaningful work, stable identity for downstream memo, or both.

**Reference solution** (`frontend/src/pages/Dashboard.jsx`):

```jsx
// useMemo for portfolio-value calc.
// useTradeStream live feed.
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

function Dashboard() {
  const { trades, isConnected } = useTradeStream();

  const portfolioValue = useMemo(
    () => trades.reduce((sum, t) => sum + (t.quantity * t.price || 0), 0),
    [trades]
  );

  const matched = trades.filter((t) => t.status === 'MATCHED').length;
  const breaks  = trades.filter((t) => ['UNMATCHED','DISPUTED'].includes(t.status)).length;

  return (
    <section>
      <h2>Dashboard</h2>
      <div className="stat-grid">
        <StatCard label="Portfolio value (USD)" value={portfolioValue.toLocaleString()} />
        <StatCard label="Trades streamed" value={trades.length} />
        <StatCard label="Matched" value={matched} />
        <StatCard label="Open breaks" value={breaks} />
      </div>
      <div role="status" aria-live="polite">
        SSE: {isConnected ? 'connected' : 'disconnected'}
      </div>
    </section>
  );
}

export default withAuth(Dashboard);
```

</details>

**▶ Run the project — verify TICKET-ADV120 end-to-end**

Backend must be emitting SSE for the live trade stream; then visit the Dashboard while authenticated.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
# Log in, then navigate to /dashboard
```

**Observe:**

- Live trades stream into the StatCard values; portfolio value updates as new trades arrive.
- React DevTools Profiler shows neither `useMemo` recomputing on unrelated state changes (e.g. opening a side panel).
- The page is `withAuth`-gated — a logged-out visit redirects to `/login`.
- Failure signal: memos recomputing on every render means an unstable dep (a new array literal) sneaked into the deps array.

---

### TICKET-ADV121 — `useCallback` on event handlers passed to memoised children

**Goal:** Wrap handlers passed into `<TradeRow />` (and similar memo
children) with `useCallback` so the memoisation from TICKET-ADV119 actually
holds.

**What**
- `const handleSelect = useCallback((id) => setSelectedId(id), [])` in `Trades.jsx` (and equivalents elsewhere), passed as the `onClick` prop on `<TradeRow />` instead of an inline arrow.

**Why**
- ADV119's memo equality includes `prev.onClick === next.onClick`; without `useCallback` every parent render rebuilds the arrow and the memo fails silently. This ticket is what makes ADV119's win measurable.

**Observe**
- Profiler with "Record why each component rendered": after this fix, `<TradeRow>` no longer appears in the commit chart with reason `props changed: onClick` on unrelated parent updates.

**Done when:**
- After this exercise, the Profiler shows `<TradeRow>` only re-renders when its `trade` props change — not when the parent re-renders for unrelated reasons.
- The dep array of every `useCallback` is honest: empty only when the closure genuinely needs nothing; otherwise it lists every captured value.
- No `useCallback` exists for handlers that are never passed into a memoised child.

<details>
<summary>Hint 1 — gentle direction</summary>

Open the Profiler with "Record why each component rendered" turned on.
Click a `<TradeRow>` in the flame chart. The right pane will tell you
*why* it rendered. Read that reason out loud before you reach for a fix.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The Profiler will say "props changed: onClick" (or similar). That's
because each render of the parent creates a brand-new arrow function.
`useCallback(fn, deps)` returns the same function reference across
renders as long as the deps are stable. Use it on the handler you pass
to `TradeRow`'s `onClick`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

In the parent: `const handleSelect = useCallback((id) => setSelectedId(id), [])`.
Pass `handleSelect` (not an inline arrow) into the row's `onClick`
prop. If the callback closes over additional state (say a filter
object), add it to the dep array — empty deps with a captured value is
the stale-closure trap.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `frontend/src/pages/Trades.jsx`; import `useCallback` and `useState`.
2. Promote the inline `(id) => setSelectedId(id)` arrow you were passing to `<TradeRow onClick={...} />` into `const handleSelect = useCallback((id) => setSelectedId(id), [])`.
3. Pass `handleSelect` (not an inline arrow) as the `onClick` prop on `<TradeRow />`.
4. Open the Profiler with "Record why each component rendered" enabled; trigger an unrelated state change.
5. Confirm `<TradeRow>` no longer re-renders with `props changed: onClick` — the memo from TICKET-ADV119 now actually holds.
6. If the handler closes over any state (filter, selection), add it to the dep array; never empty-deps with a captured value.

**Reference solution** (`frontend/src/pages/Trades.jsx`):

```jsx
// frontend/src/pages/Trades.jsx
import { useCallback, useState } from 'react';

function Trades({ trades }) {
  const [selectedId, setSelectedId] = useState(null);

  // Reference-stable across renders — onClick prop on <TradeRow> won't change
  const handleSelect = useCallback((id) => setSelectedId(id), []);

  return (
    <DataTable data={trades}>
      <DataTable.Header columns={cols} />
      <DataTable.Body
        renderRow={(t) => <TradeRow key={t.id} trade={t} onClick={handleSelect} />}
      />
    </DataTable>
  );
}
```

</details>

**▶ Run the project — verify TICKET-ADV121 end-to-end**

Open the React DevTools Profiler with "Record why each component rendered" enabled, then trigger an unrelated state change.

```bash
cd frontend && npm run dev
# React DevTools → Profiler → Settings cog → "Record why each component rendered" ON
# Record → flip an unrelated piece of state → Stop
```

**Observe:**

- `<TradeRow>` no longer appears in the commit chart with reason `props changed: onClick` — the memo from TICKET-ADV119 now actually holds.
- Total render count for rows drops to zero for the unrelated update.
- Failure signal: rows still re-rendering means an inline arrow somewhere in the parent slipped past — re-grep for `onClick={(` in the Trades page.

---

### TICKET-ADV122 — `React.lazy` + `Suspense` for route-based code splitting

**Goal:** Lazily import each page component and put one `<Suspense>`
boundary at the right level of the layout so first navigation downloads
a per-page chunk and re-navigation hits the cache.

**What**
- `const Dashboard = lazy(() => import('@pages/Dashboard'))` (one per page) wrapped in a single `<Suspense fallback={<PageSkeleton />}>` placed inside the layout so the nav/header survive navigation.

**Why**
- The Day 10 demo runs on a laptop with a single network hop; per-route chunks plus a real skeleton (not a spinner) keep first paint snappy and avoid the layout flash that distracts reviewers.

**Observe**
- Network panel: first visit to `/trades` downloads `chunk-trades-*.js`; re-navigating to `/trades` after visiting `/dashboard` does NOT redownload the chunk (HTTP 200 from disk cache).

**Done when:**
- The Network tab shows a per-route `chunk-*.js` downloading the first time you visit a page, and *not* on re-visits.
- A skeleton (not a spinner, not `null`) is shown while a chunk is loading.
- The layout (nav, header) does *not* flash on every navigation — only the page contents.

<details>
<summary>Hint 1 — gentle direction</summary>

When you `lazy(() => import(...))` without anything else, React throws
a specific error during render. Read the message — it names the
missing piece. That tells you what to add.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`Suspense` is a *boundary*: everything below it suspends when any
descendant component is still loading. Put the boundary at the layer
where suspension should produce the fallback. If you wrap each `<Route>`
individually, the layout flashes; if you wrap the whole app, the layout
also goes away during navigation. There's a level in between.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Top of `App.jsx`: `const Dashboard = lazy(() => import('@pages/Dashboard'))`
and one for each page. In the JSX, `<Layout>` wraps a single
`<Suspense fallback={<PageSkeleton />}>` which in turn wraps `<Routes>`.
That way the layout stays mounted while only the inner page suspends.
Avoid lazy-loading the layout itself.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `frontend/src/App.jsx`; import `lazy` and `Suspense` from `react`, `Routes`/`Route` from `react-router-dom`, plus the `Layout` and `PageSkeleton` components.
2. Replace each direct page import with `const Page = lazy(() => import('@pages/Page'))` for `Dashboard`, `Trades`, `Recon`, `AddTrade`, `Audit`.
3. Render `<Layout>` at the top; do **not** lazy-load it.
4. Inside the layout, mount a single `<Suspense fallback={<PageSkeleton />}>` that wraps the `<Routes>` block.
5. Inside `<Routes>`, register each `<Route path=... element={<Page />} />`.
6. Open the Network tab and click between routes; confirm a per-route `chunk-*.js` downloads first time and is cached after.
7. Confirm the layout (nav, header) does not flash during navigation — only the inner page suspends.

**Reference solution** (`frontend/src/App.jsx`):

```jsx
// Lazy + Suspense for route-based code splitting
import React, { Suspense, lazy } from 'react';
import { Routes, Route, Link, Navigate } from 'react-router-dom';
import { withErrorBoundary } from '@components/withErrorBoundary.jsx';

const Dashboard = lazy(() => import('@pages/Dashboard.jsx'));
const Trades    = lazy(() => import('@pages/Trades.jsx'));
const AddTrade  = lazy(() => import('@pages/AddTrade.jsx'));
const Login     = lazy(() => import('@pages/Login.jsx'));

function App() {
  return (
    <div className="layout">
      <header className="layout__header">
        <h1>ReconX</h1>
        <nav className="layout__nav">
          <Link to="/">Dashboard</Link>
          <Link to="/trades">Trades</Link>
          <Link to="/trades/new">Add trade</Link>
        </nav>
      </header>
      <main className="layout__main">
        <Suspense fallback={<div className="loader">Loading…</div>}>
          <Routes>
            <Route path="/login"      element={<Login />} />
            <Route path="/"           element={<Dashboard />} />
            <Route path="/trades"     element={<Trades />} />
            <Route path="/trades/new" element={<AddTrade />} />
            <Route path="*"           element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </main>
    </div>
  );
}

export default withErrorBoundary(App);
```

</details>

**▶ Run the project — verify TICKET-ADV122 end-to-end**

With the dev server running, open the Network tab and navigate between routes.

```bash
cd frontend && npm run dev
# Network tab → JS filter → click between /, /trades, /trades/new, /login
```

**Observe:**

- First visit to each route downloads a per-route `chunk-*.js` (or `assets/*.js` in build); subsequent visits are served from cache (HTTP `(disk cache)` / `(memory cache)`).
- Layout (nav, header) stays mounted between navigations — only the inner page area shows the Suspense fallback.
- Failure signal: `Error: A React component suspended while rendering, but no fallback UI was specified` means `<Suspense>` is missing or placed below where it's needed.

---

### TICKET-ADV123 — Trade entry form: RHF + Yup

**Goal:** Build the `/trades/new` page as a `react-hook-form` form
validated against a `yup` schema that covers trade ref, instrument,
quantity, price, and trade date.

**What**
- `frontend/src/pages/AddTrade.jsx` using `useForm({ resolver: yupResolver(schema), mode: 'onBlur' })` with a Yup schema covering `tradeRef` (regex `^[A-Z]{3}-\d{8}-\d{4}$`), `quantity`/`price` (`yup.number().positive()`), and `tradeDate`.

**Why**
- Day 9's break-resolution form and Day 10's compliance audit screen reuse the same RHF + Yup pattern; nailing the uncontrolled-input + `role="alert"` shape here means later forms ship as a copy-paste.

**Observe**
- Submitting empty fields fires zero network requests and renders per-field `role="alert"` messages; a valid submit POSTs `quantity: 1000` (number, not `"1000"`) to `/api/v1/trades` and returns 201; Profiler shows zero re-renders per keystroke.

**Done when:**
- Submitting a valid form sends a POST to `/api/trades` with parsed numbers (not string `"1000"`).
- Submitting an invalid form does *not* send anything; each field shows its specific error message with `role="alert"`.
- The form does not re-render on every keystroke (RHF is uncontrolled by default — verify in the Profiler).

<details>
<summary>Hint 1 — gentle direction</summary>

`react-hook-form` only pays off if you let it stay uncontrolled. The
moment you start putting `value`/`onChange` on every input by hand,
you've reinvented Formik. The job is to wire the form *once*, then
get out of the way.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`useForm` from `react-hook-form` gives you `register`, `handleSubmit`,
`reset`, and `formState`. Pair it with `yupResolver` from
`@hookform/resolvers/yup` and a `yup.object({...})` schema. Set
`mode: 'onBlur'` so the form doesn't scream red on the first keystroke.
The numeric fields need `yup.number().typeError(...)` to convert
`"1000"` cleanly.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Destructure `register` once at the top of the component — never inside
a `.map` callback. Each input becomes `<input {...register('name')} />`.
The submit handler is `handleSubmit(onSubmit)` and your `onSubmit`
receives an already-parsed/validated object. Error messages render
from `formState.errors.fieldName.message`. `defaultValues` should be
empty strings for text fields and empty strings (not `0` or `null`)
for the numeric ones — Yup coerces them once the user types.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/pages/AddTrade.jsx`; import `useForm` from `react-hook-form`, `yupResolver` from `@hookform/resolvers/yup`, `yup` namespace, and `apiService`.
2. Declare a module-scoped `yup.object({...})` schema covering `tradeRef` (regex + required), `instrument`, `quantity`/`price` (`yup.number().typeError(...).positive()`), and `tradeDate` (`yup.date().max(today)`).
3. Inside the component, call `useForm({ resolver: yupResolver(schema), mode: 'onBlur', defaultValues: { tradeRef: '', instrument: '', quantity: '', price: '', tradeDate: '' } })` and destructure `register`, `handleSubmit`, `reset`, `formState`.
4. Define an async `onSubmit(data)` that calls `apiService.createTrade(data)` then `reset()`.
5. Render the `<form onSubmit={handleSubmit(onSubmit)} noValidate>`; spread `{...register('field')}` into each input — once, never inside a `.map`.
6. Below each input, conditionally render `{errors.field && <span role="alert">{errors.field.message}</span>}`.
7. Disable the submit button while `formState.isSubmitting`; verify in the Profiler the form does **not** re-render per keystroke.

**Reference solution** (`frontend/src/pages/AddTrade.jsx`):

```jsx
// React Hook Form + Yup validation.
import React from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { withAuth } from '@components/withAuth.jsx';
import { api } from '@services/apiService.js';

const schema = yup.object({
  tradeRef:         yup.string().matches(/^[A-Z]{3}-\d{8}-\d{4}$/, 'AAA-YYYYMMDD-NNNN').required(),
  instrumentId:     yup.number().integer().positive().required(),
  counterpartyId:   yup.number().integer().positive().required(),
  assetClass:       yup.string().oneOf(['EQUITY','FX','BOND','DERIVATIVE']).required(),
  side:             yup.string().oneOf(['BUY','SELL']).required(),
  quantity:         yup.number().positive().required(),
  price:            yup.number().positive().required(),
  tradeDate:        yup.date().required(),
});

function AddTrade() {
  const { register, handleSubmit, formState: { errors, isSubmitting }, reset } =
        useForm({ resolver: yupResolver(schema) });

  async function onSubmit(values) {
    await api.createTrade(values);
    reset();
  }

  return (
    <section>
      <h2>Add trade</h2>
      <form onSubmit={handleSubmit(onSubmit)} className="trade-form">
        <label>Trade ref   <input {...register('tradeRef')} placeholder="EQU-20260603-0001" /></label>
        {errors.tradeRef && <p className="form-error">{errors.tradeRef.message}</p>}

        <label>Instrument id   <input type="number" {...register('instrumentId')} /></label>
        <label>Counterparty id <input type="number" {...register('counterpartyId')} /></label>
        <label>Asset class    <select {...register('assetClass')}>
          <option value="EQUITY">EQUITY</option><option value="FX">FX</option>
          <option value="BOND">BOND</option><option value="DERIVATIVE">DERIVATIVE</option>
        </select></label>
        <label>Side <select {...register('side')}>
          <option value="BUY">BUY</option><option value="SELL">SELL</option>
        </select></label>
        <label>Quantity  <input type="number" step="0.0001" {...register('quantity')} /></label>
        <label>Price     <input type="number" step="0.0001" {...register('price')} /></label>
        <label>Trade date<input type="date" {...register('tradeDate')} /></label>

        <button disabled={isSubmitting} type="submit">Submit</button>
      </form>
    </section>
  );
}

export default withAuth(AddTrade);
```

</details>

**▶ Run the project — verify TICKET-ADV123 end-to-end**

Backend must be running so the POST is accepted; then submit one invalid and one valid form.

```bash
# Terminal 1 — backend
./mvnw spring-boot:run
# Terminal 2 — frontend
cd frontend && npm run dev
# log in, navigate to /trades/new
```

**Observe:**

- Submitting an empty/invalid form does **not** fire a network request; each invalid field renders its specific message in a `role="alert"` element.
- A valid submission POSTs to `/api/v1/trades` with parsed numbers (`quantity: 1000`, not `"1000"`) and returns `201`.
- React DevTools Profiler shows the form does **not** re-render per keystroke (RHF stays uncontrolled).
- Failure signal: numeric fields arriving at the API as strings means `yup.number().typeError(...)` is missing on the schema.

---

### TICKET-ADV124 — Theme context (light/dark)

**Goal:** Provide a `ThemeProvider` that exposes a `useTheme()` hook,
persists the choice to `localStorage`, respects `prefers-color-scheme`
on first visit, and flips `data-theme` on `<html>` so the existing CSS
takes over.

**What**
- `frontend/src/context/ThemeContext.jsx` exposing `<ThemeProvider>` + `useTheme()`, seeded from `localStorage.theme` then `prefers-color-scheme`, with a `useEffect` that flips `document.documentElement.dataset.theme`.

**Why**
- Day 7 already ships `[data-theme="dark"]` CSS overrides on top of the `--color-primary` token set; React owns the flip, CSS owns the values, and the same provider serves Day 9's settings page toggle.

**Observe**
- DevTools Elements panel: `<html data-theme="dark">` appears the moment the toggle is clicked; reloading the page restores the same attribute; clearing `localStorage` and reloading on a system in dark mode seeds dark on first paint.

**Done when:**
- Toggling theme flips a single attribute on the `<html>` element and the page restyles immediately — no JS-side colour values.
- Reloading the page preserves the chosen theme.
- First-time visitors with `prefers-color-scheme: dark` start in dark mode.

<details>
<summary>Hint 1 — gentle direction</summary>

Day 7's CSS already uses `[data-theme="dark"]` selectors. React doesn't
need to know any colour values — it owns the *flip*, CSS owns the
*values*. That means your context state is just one string.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`createContext` + `useState` + one `useEffect` that writes
`document.documentElement.dataset.theme = theme` and also persists to
`localStorage`. For the initial value, a lazy initialiser function
returns the stored value if present, otherwise checks
`window.matchMedia('(prefers-color-scheme: dark)').matches`. The hook
throws if used outside the provider.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Provider component owns `[theme, setTheme]` plus a memoised `toggle`
callback. The provider value is `{ theme, setTheme, toggle }`. Lift the
provider to the top of `main.jsx` (above `BrowserRouter` and definitely
above any ErrorBoundary HOC — otherwise the boundary's fallback can't
read theme). The hook just calls `useContext` and asserts non-null.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/context/ThemeContext.jsx`; import `createContext`, `useContext`, `useEffect`, `useState`, `useCallback`.
2. Declare a module-scoped `ThemeContext = createContext(null)` and a `STORAGE_KEY` constant.
3. Write a lazy `initialTheme()` function that reads `localStorage`, falls back to `window.matchMedia('(prefers-color-scheme: dark)').matches`, and guards `typeof window === 'undefined'`.
4. Export `ThemeProvider({ children })` owning `[theme, setTheme] = useState(initialTheme)`.
5. Add a `useEffect([theme])` that writes `document.documentElement.dataset.theme = theme` and `localStorage.setItem(STORAGE_KEY, theme)`.
6. Add a `toggle = useCallback(() => setTheme(t => t === 'light' ? 'dark' : 'light'), [])` and expose `{ theme, setTheme, toggle }` as the provider value.
7. Export `useTheme()` which calls `useContext(ThemeContext)` and throws if null.
8. Lift `<ThemeProvider>` to `main.jsx` above `<BrowserRouter>` so the ErrorBoundary fallback can still read theme.

**Reference solution** (`frontend/src/context/ThemeContext.jsx`, `frontend/src/main.jsx`):

```jsx
// ThemeProvider: context flips data-theme; CSS owns colours.
import React, { createContext, useContext, useEffect, useState } from 'react';

const ThemeContext = createContext({ theme: 'light', toggle: () => {} });

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(
    () => localStorage.getItem('reconx-theme') || 'light'
  );

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('reconx-theme', theme);
  }, [theme]);

  const toggle = () => setTheme((t) => (t === 'light' ? 'dark' : 'light'));

  return (
    <ThemeContext.Provider value={{ theme, toggle }}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => useContext(ThemeContext);
```

```jsx
// Entry point; mounts <App /> inside ThemeProvider + Router.
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import { ThemeProvider } from '@context/ThemeContext.jsx';
import { AuthProvider } from '@context/AuthContext.jsx';
import './styles/global.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  </React.StrictMode>
);
```

</details>

**▶ Run the project — verify TICKET-ADV124 end-to-end**

Boot the app, click the theme toggle, then reload to check persistence.

```bash
cd frontend && npm run dev
# click the theme toggle, then reload the page
```

**Observe:**

- Clicking the toggle flips `<html data-theme="dark">` ↔ `<html data-theme="light">`; the existing CSS restyles the page instantly.
- `localStorage.getItem('reconx-theme')` matches the selected value; reloading preserves the theme.
- First-time visitors with `prefers-color-scheme: dark` (DevTools → Rendering → Emulate CSS media feature) start in dark mode.
- Failure signal: a thrown error from `useTheme()` saying "outside provider" means `<ThemeProvider>` isn't lifted high enough in `main.jsx`.

---

### Workshop 8D — RTL tests and the Profiler

Tests and profiling close the day. Two short RTL tests prove the app
keeps working when you refactor; one Profiler trace turns
"the dashboard feels slow" into a specific named culprit and a fix.

### TICKET-ADV125 — RTL test: dashboard summary cards

**Goal:** Render `<Dashboard />` with seeded trade data inside the
required providers and assert each summary card is present.

**What**
- `frontend/src/pages/Dashboard.test.jsx` with a `renderWithProviders(ui)` helper wrapping `ui` in `AuthContext.Provider` -> `ThemeProvider` -> `MemoryRouter`, and one synchronous `it('shows summary cards', ...)` asserting three `getByRole('heading', { name: /.../i })` + one regex value check.

**Why**
- Day 9's RTL coverage and the Day 10 CI gate both build on this provider helper; using role queries (not `getByText` / `getByTestId`) keeps the tests resilient to copy changes and locale tweaks.

**Observe**
- `npm test -- --run Dashboard` reports `1 passed` with no live backend running; the test is fully synchronous (no `waitFor`); removing `MemoryRouter` from the helper reproduces `useNavigate() may be used only in the context of a <Router>` as the failure signal.

**Done when:**
- `npm run test` (or `vitest run`) passes the dashboard test in isolation, with no live backend.
- Queries use roles (`getByRole('heading', { name: /portfolio value/i })`) — not `getByText` and not `getByTestId`.
- A `renderWithProviders(ui)` helper wraps the UI in `MemoryRouter`, `ThemeProvider`, and `AuthContext.Provider` so the test does not crash on `useNavigate` / `useTheme` calls.

<details>
<summary>Hint 1 — gentle direction</summary>

The first time you `render(<Dashboard />)` it will throw something
about `useNavigate` or `useAuth`. Tests render in isolation; none of
your providers exist. What's the smallest shim that puts them back in
the test tree?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Factor a `renderWithProviders(ui)` helper colocated with the test (or
in a shared `test/utils.jsx`) that wraps `ui` in `AuthContext.Provider`
with a fake user, then `ThemeProvider`, then `MemoryRouter`. Use
`screen.getByRole('heading', { name: ... })` for the card headings,
and a regex like `/37,550/` for the computed numeric value so the test
survives locale formatting tweaks.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Seed `trades` as a small array literal with known quantities and
prices so you can compute the expected portfolio value by hand. The
`describe` block has one `it('shows summary cards', ...)` that calls
the provider helper and runs three `expect(screen.getByRole(...)).toBeInTheDocument()`
checks plus one numeric assertion. No async, no `waitFor` — this test
should be fully synchronous.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `frontend/src/pages/Dashboard.test.jsx`; import Vitest's `describe`/`it`/`expect`, `render`/`screen` from RTL, `MemoryRouter` from `react-router-dom`, the `ThemeProvider`, the `AuthContext`, and the `Dashboard` page.
2. Seed a small `trades` array literal with known quantities and prices so you can compute the expected portfolio value (37,550) by hand.
3. Write a `renderWithProviders(ui)` helper that wraps `ui` in `<AuthContext.Provider value={{ user: fake, isLoading: false }}>` → `<ThemeProvider>` → `<MemoryRouter>`.
4. In one `it('shows summary cards', ...)`, call the helper with `<Dashboard trades={trades} />`.
5. Assert each card heading with `screen.getByRole('heading', { name: /…/i })` — three calls — no `getByText` for labels.
6. Add one numeric assertion via a regex (`/37,550/`) so locale formatting tweaks don't break the test.
7. Run `npm run test`; the test should be fully synchronous (no `waitFor`).

**Reference solution** (`frontend/src/pages/Dashboard.test.jsx`):

```jsx
// frontend/src/pages/Dashboard.test.jsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@context/ThemeContext';
import { AuthContext }   from '@context/AuthContext';
import Dashboard         from './Dashboard';

const trades = [
  { id: 1, tradeRef: 'TRD-2026-0001', instrument: 'SAP.DE', quantity: 100, price: 250, status: 'MATCHED'   },
  { id: 2, tradeRef: 'TRD-2026-0002', instrument: 'SAP.DE', quantity: 50,  price: 251, status: 'UNMATCHED' },
];

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
    renderWithProviders(<Dashboard trades={trades} />);

    expect(screen.getByRole('heading', { name: /portfolio value/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /matched trades/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /unmatched trades/i })).toBeInTheDocument();
    // 100 * 250 + 50 * 251 = 37550
    expect(screen.getByText(/37,550/)).toBeInTheDocument();
  });
});
```

</details>

**▶ Run the project — verify TICKET-ADV125 end-to-end**

Run the Vitest test in one-shot mode (Vitest watches by default).

```bash
cd frontend && npm test -- --run Dashboard
```

**Observe:**

- The `<Dashboard />` test passes green; output reports `1 passed`.
- The run completes without spinning up a live backend (providers are stubbed via `renderWithProviders`).
- Queries use roles (`getByRole('heading', ...)`) — confirmed by the test output not warning about `getByText` fallbacks.
- Failure signal: `useNavigate() may be used only in the context of a <Router>` means `MemoryRouter` is missing from the helper.

---

## Fast Finishers / Stretch Goals (Optional)

These exercises are **not** part of the main Day 8 sprint. Pick them up
if you finished TICKET-ADV125 with time on the clock, or revisit them
during Day-9 buffer time. Nothing in the final demo or the rest of the
project depends on these — they're here to deepen the techniques you
already learned today.

### TICKET-ADV127 — Profile with React DevTools, fix unnecessary re-renders *(fast-finisher)*

**Goal:** Open the React DevTools Profiler, record a session that
exposes excessive re-renders on the dashboard, identify the specific
culprit, and apply a fix that reduces the render count in a follow-up
recording.

**What**
- A *before* Profiler trace showing the excess renders, a fix (typically hoisting an inline arrow into `useCallback` or memoising a context provider's value with `useMemo`), and an *after* trace with the same interactions and materially fewer renders for the named culprit.

**Why**
- ADV119 + ADV121 set up the pieces; ADV127 is where you prove with measurement (not vibes) that the memo strategy actually pays off — the same evidence shape Day 10's demo deck leans on.

**Observe**
- React DevTools Profiler flame chart: the named row component drops from `N` commits to ~1 across identical recorded interactions; `<Profiler id="TradeDashboard" onRender>` console-logs `actual=` shrinking measurably; the "Highlight updates" tint stops flashing on unrelated state flips.

**Done when:**
- You have a *before* Profiler trace showing the bug (excessive render counts on a component that didn't need to update).
- You can articulate *why* the component rendered (which prop changed, or which parent caused it).
- An *after* recording — same interactions, same data — shows materially fewer renders for the same component, and you can name the change that did it.

<details>
<summary>Hint 1 — gentle direction</summary>

Profiler traces are useless without interaction. Hit record, *do
something* in the app (click a row, type in the search box, scroll),
then stop. The flame chart shows what rendered during your recording,
not what's mounted.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Click the largest yellow bar in the flame chart and look at the right
panel — it tells you *why* the component rendered. Toggle "Highlight
updates when components render" on the Components tab too; you'll see
rows literally flash on every parent update. Common culprits: parents
passing inline `style={{...}}` or `onClick={() => ...}` props,
`useEffect` with no deps array calling `setState` every render, or a
context provider whose value object is rebuilt every render.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Typical fix shape: hoist the inline arrow into a `useCallback` (or a
module-level constant if no closure is needed), or memoise the
context provider's value with `useMemo`. If the row component itself
isn't memoised, that's a precondition — TICKET-ADV119 + TICKET-ADV121 together are
what makes TICKET-ADV127's fix visible. Optionally wrap the dashboard in
`<Profiler id="TradeDashboard" onRender={fn}>` for one debugging
session to log `actualDuration` vs `baseDuration` for each render.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open React DevTools → Components tab, click the cog, toggle "Highlight updates when components render".
2. Switch to the Profiler tab, hit record, click a row / type in the search box / navigate, then stop. Capture this as your *before* trace.
3. Click the largest yellow bar in the flame chart; read the right panel's "Why did this render?" reason (typically `props changed: onClick`).
4. Identify the inline arrow or unstable object causing the prop churn (commonly `onClick={() => ...}` or `style={{...}}` passed to a memoised child).
5. In `Dashboard.jsx`, optionally wrap the contents in `<Profiler id="TradeDashboard" onRender={fn}>` to log `actualDuration` vs `baseDuration` for one debugging session.
6. Apply the fix: hoist the inline arrow into a `useCallback`, or memoise the context provider's value with `useMemo`, or move a module-level constant out of the component entirely.
7. Record again with the same interactions; confirm the row's render count drops materially and the highlight stops flashing on unrelated updates.

**Reference solution** (`frontend/src/pages/Dashboard.jsx`):

```jsx
// frontend/src/pages/Dashboard.jsx — wrap with <Profiler> for one debugging session
import { Profiler } from 'react';

function onRender(id, phase, actualDuration, baseDuration) {
  // eslint-disable-next-line no-console
  console.log(`[Profiler] ${id} ${phase}  actual=${actualDuration.toFixed(2)}ms  base=${baseDuration.toFixed(2)}ms`);
}

export default function Dashboard({ trades }) {
  return (
    <Profiler id="TradeDashboard" onRender={onRender}>
      <DashboardContents trades={trades} />
    </Profiler>
  );
}
```

The actual fix typically looks like:

```jsx
// Before — inline arrow, new identity every render, memo bypassed
<TradeRow trade={t} onClick={(id) => setSelected(id)} />

// After — useCallback stabilises the reference
const handleSelect = useCallback((id) => setSelected(id), []);
<TradeRow trade={t} onClick={handleSelect} />
```

</details>

**▶ Run the project — verify TICKET-ADV127 end-to-end**

Wrap a slow component in `<Profiler>`, then record before/after traces in React DevTools.

```bash
cd frontend && npm run dev
# React DevTools → Profiler → Record → interact → Stop  (before)
# apply the useCallback / useMemo fix
# Record the same interactions again                    (after)
```

**Observe:**

- The console logs `[Profiler] TradeDashboard ... actual=X.XXms base=Y.YYms` for each commit while the wrapper is in place.
- The *after* trace shows materially fewer renders for the named culprit; the highlight stops flashing on unrelated updates.
- You can name in one sentence what changed (e.g. "hoisted the inline `onClick` arrow into a `useCallback`, so the memoised `<TradeRow>` now skips re-renders on parent state changes").
- Failure signal: identical render counts before and after means the fix targeted the wrong prop — re-read the Profiler's "Why did this render?" panel before changing anything else.

---

## End-of-day checklist

By 17:00 every team should be able to tick all of these. If any are
red, surface them in the debrief — Day 9 builds directly on the green
ones.

- [ ] Vite dev server boots on `http://localhost:5173` with `@`, `@components`, `@hooks`, `@services`, and `@context` aliases resolving in both runtime and IDE.
- [ ] `withAuth` and `withErrorBoundary` HOCs exist in `src/hocs/` and wrap at least the Dashboard page.
- [ ] `<DataTable>` compound component renders the trade list with clickable sort headers and a working `<DataTable.Pagination />`; removing the pagination still works.
- [ ] Four custom hooks live under `src/hooks/` — `useWebSocket`, `useTradeStream`, `useDebouncedSearch`, `useInfiniteScroll` — each consumed by at least one page or component.
- [ ] `<TradeRow />` is wrapped in `React.memo` with custom equality, and the handlers it receives are `useCallback`-stable.
- [ ] Each route is `React.lazy`-loaded with one shared `<Suspense fallback={<PageSkeleton />}>` boundary inside the layout.
- [ ] `/trades/new` is a `react-hook-form` form with a `yup` schema; valid submissions POST to `/api/trades`, invalid ones surface inline messages with `role="alert"`.
- [ ] `ThemeProvider` flips `data-theme` on `<html>`, persists the choice, and seeds from `prefers-color-scheme` on first load.
- [ ] At least one RTL test passes for the dashboard summary cards.
- [ ] *(Optional fast-finisher)* You can show a *before* and *after* React DevTools Profiler trace for the TICKET-ADV127 fix and explain in one sentence what changed.
