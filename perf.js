import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// TICKET-ADV097 — Performance test: 100 concurrent trade POSTs at concurrency 10
export const options = {
  vus: 10,
  iterations: 100,
  thresholds: {
    http_req_failed:   ['rate<0.01'],          // <1% errors
    http_req_duration: ['p(95)<2000'],         // P95 under 2 s
  },
};

const TOKEN = __ENV.TOKEN;
const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export default function () {
  // Each VU+iteration gets a unique tradeRef matching ^[A-Z]{3}-\d{8}-\d{4}$
  const seq = String(__ITER + 1).padStart(4, '0');
  const vu  = String(__VU).padStart(2, '0');
  const tradeRef = `PRF-20260731-${vu}${seq.slice(2)}`;  // e.g. PRF-20260731-0101

  const payload = JSON.stringify({
    tradeRef:      tradeRef,
    instrumentId:  1,
    counterpartyId: 1,
    assetClass:    'EQUITY',
    side:          'BUY',
    quantity:      100,
    price:         245.5,
    tradeDate:     '2026-07-30',
  });

  const res = http.post(`${BASE_URL}/api/v1/trades`, payload, {
    headers: {
      'Authorization': `Bearer ${TOKEN}`,
      'Content-Type':  'application/json',
    },
  });

  check(res, {
    'status is 201': (r) => r.status === 201,
  });
}
