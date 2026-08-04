#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] Postgres..."
docker compose up -d Postgresfor i in {1..10}; do
    s=$(docker inspect --format='{{.State.Health.Status}}' reconx-postgres 2>/dev/null || echo starting)
    [[ "$s" == "healthy" ]] && { echo "  postgres healthy"; break; }
    sleep 1
done
[[ "$s" == "healthy" ]] || { echo "postgres not healthy"; docker exec reconx-postgres pg_isready -U reconx_user -d reconx; exit 1; }

echo "[2/3] Kafka..."
docker compose up -d kafka
for i in {1..15}; do
    s=$(docker inspect --format='{{.State.Health.Status}}' reconx-kafka 2>/dev/null || echo starting)
    [[ "$s" == "healthy" ]] && { echo "  kafka healthy"; break; }
    sleep 2
done
[[ "$s" == "healthy" ]] || { echo "kafka not healthy"; docker exec reconx-kafka kafka-topics --bootstrap-server localhost:9092 --list; exit 1; }

echo "[3/3] Backend..."
docker compose up -d Backend
for i in {1..20}; do
    s=$(docker inspect --format='{{.State.Health.Status}}' reconx-backend 2>/dev/null || echo starting)
    [[ "$s" == "healthy" ]] && { echo "  backend healthy"; break; }
    sleep 3
done[[ "$s" == "healthy" ]] || { echo "backend not healthy"; docker exec reconx-backend wget -qO- http://localhost:8080/api/actuator/health; exit 1; }

echo "All healthchecks green."