# Campus Connect

Multi-tenant placement portal for Tier-2/3 colleges — replacing WhatsApp + Excel with
automated eligibility, application tracking, and reports.

> **Status:** Story 1.1 — project scaffolding & local infrastructure. Skeleton only; business
> logic arrives story by story (see `_bmad-output/` planning artifacts).

## Architecture (at a glance)

A tenant-aware modular system: a shared `common-lib` domain core, three role-scoped Spring Boot
services + an API gateway, all over **one shared MongoDB**.

```
  Angular SPA  →  api-gateway (8080)  →  student-service  (8081)
                                         recruiter-service (8082)
                                         admin-service     (8083)
                       each imports →  common-lib  →  MongoDB · MinIO · Mailpit
```

## Prerequisites

| Tool | Version |
|---|---|
| JDK | **21** (LTS) |
| Maven | **3.9+** |
| Node.js | **22.22.3+** (Angular 22 requirement) |
| Docker + Compose | recent (Compose v2) |

## Run it (one command for infra, then the apps)

```bash
# 1) Start local infrastructure (MongoDB, MinIO, Mailpit)
docker compose up -d

# 2) Build everything (reactor: common-lib + 3 services + gateway)
mvn clean install

# 3) Run a service (repeat per service, or run from your IDE)
mvn -pl student-service spring-boot:run
mvn -pl recruiter-service spring-boot:run
mvn -pl admin-service spring-boot:run
mvn -pl api-gateway spring-boot:run

# 4) Build the frontend
cd frontend && npm install && npm run build
```

## Port map

| Component | URL / Port |
|---|---|
| API Gateway | http://localhost:8080 |
| student-service | http://localhost:8081 — Swagger: `/swagger-ui.html`, health: `/actuator/health` |
| recruiter-service | http://localhost:8082 — Swagger: `/swagger-ui.html`, health: `/actuator/health` |
| admin-service | http://localhost:8083 — Swagger: `/swagger-ui.html`, health: `/actuator/health` |
| MongoDB | localhost:27017 |
| MinIO | API http://localhost:9000 · Console http://localhost:9101 (minioadmin / minioadmin) |
| Mailpit | SMTP localhost:1025 · Web UI http://localhost:8025 |

> **Note:** MinIO's console is mapped to host port **9101** (not the conventional 9001) to avoid a
> clash with another process already bound to 9001 on this machine. The container-internal port is
> still 9001. Change the mapping in `docker-compose.yml` if 9001 is free on your host.

## Module layout

```
campus-connect/
├── pom.xml                  # Maven reactor parent (Spring Boot 4.0.6, Java 21)
├── docker-compose.yml       # dev infra: mongo, minio, mailpit
├── common-lib/              # shared domain core (library jar — skeleton)
├── api-gateway/             # Spring Cloud Gateway (reactive, 8080)
├── student-service/         # 8081
├── recruiter-service/       # 8082
├── admin-service/           # 8083
└── frontend/                # Angular 22 workspace
```

## Version pins worth knowing

- **Spring Boot 4.0.6** (do not downgrade to 3.x — 3.5 EOL 30 Jun 2026).
- **Spring Cloud 2025.1.1** — the Boot-4.0 train (2025.0.x targets Boot 3.5).
- **springdoc-openapi 3.0.3** — Boot-4 compatible (2.8.x is Boot-3 only).
- The gateway uses `spring-cloud-starter-gateway-server-webflux` (the Boot-4 reactive starter).
