// Fetch wrapper that attaches Bearer JWT from sessionStorage.
const BASE = '/api';

function authHeaders() {
  const token = typeof sessionStorage !== 'undefined'
    ? sessionStorage.getItem('reconx-token')
    : null;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(method, path, body) {
  const headers = {
    'Content-Type': 'application/json',
    ...authHeaders(),
  };
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const payload = await res.text();
      if (payload) detail = payload;
    } catch { /* ignore parse errors */ }
    throw new Error(`HTTP ${res.status}: ${detail}`);
  }
  if (res.status === 204) return null;
  const contentType = res.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) return null;
  return res.json();
}

export const api = {
  login: (email, password)   => request('POST',   '/auth/login', { email, password }),
  listTrades: (params = '')  => request('GET',    `/v1/trades${params ? `?${params}` : ''}`),
  createTrade: (req)         => request('POST',   '/v1/trades', req),
  updateStatus: (id, status) => request('PATCH',  `/v1/trades/${id}/status`, { status }),
  deleteTrade: (id)          => request('DELETE', `/v1/trades/${id}`),
  runRecon: (req)            => request('POST',   '/v1/recon/run', req),
  reconResults: (jobId)      => request('GET',    `/v1/recon/jobs/${jobId}/results`),
  audit: (tradeRef)          => request('GET',    `/v1/audit/trades/${tradeRef}`),
};
