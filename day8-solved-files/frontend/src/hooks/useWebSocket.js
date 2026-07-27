// useWebSocket(url) with auto-reconnect (exponential backoff up to `maxRetries`).
import { useCallback, useEffect, useRef, useState } from 'react';

export function useWebSocket(url, { reconnect = true, maxRetries = 5 } = {}) {
  const [data, setData] = useState(null);
  const [status, setStatus] = useState('connecting');
  const wsRef = useRef(null);
  const retriesRef = useRef(0);
  const timerRef = useRef(null);
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;
    retriesRef.current = 0;

    function connect() {
      if (cancelledRef.current) return;
      const ws = new WebSocket(url);
      wsRef.current = ws;
      setStatus('connecting');

      ws.onopen = () => {
        if (cancelledRef.current) return;
        setStatus('open');
        retriesRef.current = 0;
      };
      ws.onmessage = (event) => {
        if (cancelledRef.current) return;
        try {
          setData(JSON.parse(event.data));
        } catch {
          setData(event.data);
        }
      };
      ws.onerror = () => {
        if (cancelledRef.current) return;
        setStatus('error');
      };
      ws.onclose = () => {
        if (cancelledRef.current) return;
        setStatus('closed');
        if (reconnect && retriesRef.current < maxRetries) {
          const attempt = retriesRef.current++;
          const delay = Math.min(30000, 500 * 2 ** attempt);
          timerRef.current = setTimeout(connect, delay);
        }
      };
    }

    connect();

    return () => {
      cancelledRef.current = true;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      const ws = wsRef.current;
      if (ws && ws.readyState <= WebSocket.OPEN) {
        ws.close();
      }
    };
  }, [url, reconnect, maxRetries]);

  const send = useCallback((payload) => {
    const ws = wsRef.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(typeof payload === 'string' ? payload : JSON.stringify(payload));
  }, []);

  return { data, status, send };
}
