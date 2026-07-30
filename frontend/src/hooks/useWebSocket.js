// TICKET-ADV115 — useWebSocket(url) with auto-reconnect (exp backoff up to 5 tries).
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

      ws.onopen = () => {
        if (!cancelled) {
          setStatus('open');
          retries.current = 0;
        }
      };

      ws.onmessage = (e) => {
        if (!cancelled) {
          try {
            setData(JSON.parse(e.data));
          } catch {
            setData(e.data);
          }
        }
      };

      ws.onerror = () => {
        if (!cancelled) setStatus('error');
      };

      ws.onclose = () => {
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
      if (wsRef.current && wsRef.current.readyState <= WebSocket.OPEN) {
        wsRef.current.close();
      }
    };
  }, [url, reconnect, maxRetries]);

  const send = (payload) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(typeof payload === 'string' ? payload : JSON.stringify(payload));
    }
  };

  return { data, status, send };
}
