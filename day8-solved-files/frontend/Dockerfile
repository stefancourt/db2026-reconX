# ============================================================================
# TICKET-ADV147 — React multi-stage build (npm -> nginx)
# ============================================================================
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
# `npm install` (not `npm ci`) so a stale package-lock.json doesn't block the
# image build. Trade-off: not bit-for-bit deterministic. For a real prod
# image switch to `npm ci` and treat lockfile freshness as a PR gate.
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
