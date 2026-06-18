# syntax=docker/dockerfile:1
# Caddy image that BOTH serves the built Angular SPA and reverse-proxies /api (Story 10.3).
# Build context MUST be the repo root:  docker build -f caddy/Dockerfile .

# ---- Stage 1: build the SPA (Node 22, matching CI) ----
FROM node:22-alpine AS spa
WORKDIR /app/frontend
# Dependency layer caches across source-only changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
# @angular/build:application emits to dist/frontend/browser

# ---- Stage 2: Caddy + the static bundle + the Caddyfile ----
FROM caddy:2-alpine AS runtime
COPY caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=spa /app/frontend/dist/frontend/browser /srv
EXPOSE 80 443
# (uses the base image's default entrypoint: caddy run --config /etc/caddy/Caddyfile)
