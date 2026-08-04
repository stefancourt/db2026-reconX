// ============================================================================
// File: loadtest/trade-creation.js
// TICKET-ADV158 — k6 load test: 200 concurrent users posting trades for 2 min
// Run:  k6 run loadtest/trade-creation.js
//       BASE_URL=http://localhost:8080 k6 run loadtest/trade-creation.js
// ============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Custom metrics — allow asserting thresholds beyond built-in http_req_duration
const tradeLatency = new Trend('trade_post_latency_ms');
const errorRate    = new Rate('trade_post_errors');

export const options = {
  scenarios: {
    constant_load: {
      executor:     'constant-vus',
      vus:          200,
      duration:     '2m',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    // p95 latency must be under 800ms, p99 under 2s
    'trade_post_latency_ms': ['p(95)<800', 'p(99)<2000'],
    // custom error rate under 2%
    'trade_post_errors':     ['rate<0.02'],
    // built-in http failure rate under 2%
    'http_req_failed':       ['rate<0.02'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// setup() runs once before VUs spin up — log in once, share JWT with all VUs
export function setup() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: 'trader@db.com', password: 'trader123' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const token = res.json('token');
  if (!token) {
    console.error(`Login failed: ${res.status} ${res.body}`);
  }
  return { token };
}

// Default function runs on every VU iteration
export default function (data) {
  // Unique tradeRef per VU+iteration so DB uniqueness constraint is never violated
  const tradeRef = `LT-${__VU}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    tradeRef:         tradeRef,
    instrumentSymbol: 'SAP.DE',
    counterpartyLei:  '5493001ABCDE12345001',
    quantity:         100 + (__VU % 50),
    price:            245.50 + (__ITER % 10) * 0.01,
    tradeDate:        '2026-06-02',
  });

  const t0  = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/trades`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization:  `Bearer ${data.token}`,
    },
  });
  tradeLatency.add(Date.now() - t0);

  const ok = check(res, {
    '201 created':  r => r.status === 201,
    'has trade id': r => !!r.json('id'),
  });
  errorRate.add(!ok);

  sleep(0.5);
}
