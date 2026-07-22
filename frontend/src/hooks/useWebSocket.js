// TICKET-ADV115 — useWebSocket(url) with auto-reconnect (exp backoff up to 5 tries).
import { useState } from 'react';

export function useWebSocket(url, { reconnect = true, maxRetries = 5 } = {}) {
  // TODO(TICKET-ADV115): open a WebSocket in a useEffect.
  //   - track readyState in `status` ('connecting' | 'open' | 'closed' | 'error').
  //   - parse incoming messages as JSON (fall back to raw string).
  //   - on close, if `reconnect` and retries < maxRetries, schedule another
  //     connect() with exponential backoff (500 * 2^attempt, capped at 30s).
  //   - cleanup must close the socket AND cancel any pending reconnect.
  const [data /*, setData */] = useState(null);
  const [status /*, setStatus */] = useState('connecting');

  const send = (/* payload */) => {
    // TODO(TICKET-ADV115): only send if the socket exists AND readyState === OPEN.
    //                     Serialize non-string payloads via JSON.stringify.
  };

  return { data, status, send };
}
