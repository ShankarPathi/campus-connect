# syntax=docker/dockerfile:1
# Frontend image: build the Angular SPA, then serve it via nginx and reverse-proxy /api → api-gateway.
# Used by the lightweight AWS deploy (HTTP on the instance IP). Build context = repo root:
#   docker build -f frontend/Dockerfile .

# ---- Stage 1: build the SPA (Node 22, matching CI) ----
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
# @angular/build:application emits to dist/frontend/browser

# ---- Stage 2: nginx serving the static bundle + proxying /api ----
FROM nginx:1.27-alpine AS runtime
COPY frontend/deploy-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
