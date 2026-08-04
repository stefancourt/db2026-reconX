#!/usr/bin/env bash
# ============================================================================
# File: scripts/smoke-test.sh
# TICKET-ADV153 — End-to-end smoke test for the full 7-service stack
# Run from repo root: bash scripts/smoke-test.sh
# ============================================================================
set -euo pipefail

echo "▶ 1/7  Bringing the stack up..."
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d
echo "  Waiting up to 90s for backend to be healthy..."
for i in {1..18}; do
    status=$(docker inspect --format='{{.State.Health.Status}}' reconx-backend 2>/dev/null || echo starting)
    [[ "$status" == "healthy" ]] && break
    sleep 5
done
[[ "$status" == "healthy" ]] || { echo "✗ backend not healthy"; exit 1; }
echo "  ✓ backend healthy"

echo "▶ 2/7  Logging in as trader..."
TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || { echo "✗ login failed"; exit 1; }
echo "  ✓ JWT acquired"

echo "▶ 3/7  Posting a trade..."
TRADE=$(curl -fsS -X POST http://localhost:8080/api/v1/trades \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"tradeRef":"SMOKE-001","instrumentSymbol":"SAP.DE","counterpartyLei":"5493001ABCDE12345001","quantity":100,"price":245.5,"tradeDate":"2026-06-02"}')
echo "  ✓ trade created: $(echo "$TRADE" | jq -r .id)"

echo "▶ 4/7  Confirming Kafka event..."
sleep 3
docker exec reconx-kafka kafka-console-consumer \
    --bootstrap-server kafka:29092 --topic trade-events \
    --from-beginning --max-messages 1 --timeout-ms 10000 | grep -q SMOKE-001 \
    && echo "  ✓ trade-event found on topic" || { echo "✗ no Kafka event"; exit 1; }

echo "▶ 5/7  Confirming Postgres audit row..."
docker exec reconx-postgres psql -U reconx_user -d reconx -tAc \
    "SELECT COUNT(*) FROM audit_log WHERE table_name='trades';" | grep -qv '^0$' \
    && echo "  ✓ audit row present" || { echo "✗ no audit row"; exit 1; }

echo "▶ 6/7  Confirming Prometheus scrape..."
curl -fsS http://localhost:9090/api/v1/query?query=up\{job=\"spring-boot\"\} \
    | jq -e '.data.result[0].value[1]=="1"' >/dev/null \
    && echo "  ✓ Prometheus scraping backend" || { echo "✗ Prometheus target DOWN"; exit 1; }

echo "▶ 7/7  Confirming Grafana datasource..."
curl -fsS -u admin:admin http://localhost:3000/api/datasources/uid/reconx-prometheus \
    | jq -e '.uid=="reconx-prometheus"' >/dev/null \
    && echo "  ✓ Grafana datasource provisioned" || { echo "✗ Grafana datasource missing"; exit 1; }

echo
echo "✅  All 7 checks green — stack is demo-ready.""})