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

## Tenant isolation gate (NFR-1)

No college may ever read another's data. This is enforced structurally and guarded by CI:

- **Mechanism (proven).** Every tenant-scoped document carries `tenantId`; `common-lib`'s `TenantAwareRepository` auto-stamps it on write and auto-filters it on read, so the standard methods cannot express a tenant-leaking query. This auto-filter is proven by `TenantAwareRepositoryTest` (real MongoDB: data written under Tenant A is invisible to Tenant B; cross-tenant writes/overwrites are rejected).
- **The endpoint gate (grows per endpoint).** `CrossTenantIsolationTest` (in `student-service`) asserts that a tenant's scope never yields another tenant's data through the real endpoints. **It covers only the tenant-scoped surface that exists today** — the `users` collection created by the bootstrap endpoint (per-tenant separation + per-tenant, not global, email uniqueness). It is the extension point that grows as tenant-scoped read endpoints land (profiles, drives, applications, offers, reports — Epics 3–8). It does **not** yet cover those endpoints, because they don't exist yet.
  - **Rule:** every new tenant-scoped list/detail endpoint MUST add an "A-cannot-read-B" case to `CrossTenantIsolationTest`. A failure there fails the build.
- **CI.** `.github/workflows/ci.yml` runs `./mvnw -B clean verify` (the full suite, including both isolation tests) on every push/PR to `main`, then explicitly asserts the isolation gate **executed and passed** (so a future `@Disabled`/exclusion can't leave CI green). A regression in either isolation test fails the build.

> The SonarQube coverage gate, image publishing, and deployment are added in Epic 10 (Story 10.2).

## Version pins worth knowing

- **Spring Boot 4.0.6** (do not downgrade to 3.x — 3.5 EOL 30 Jun 2026).
- **Spring Cloud 2025.1.1** — the Boot-4.0 train (2025.0.x targets Boot 3.5).
- **springdoc-openapi 3.0.3** — Boot-4 compatible (2.8.x is Boot-3 only).
- The gateway uses `spring-cloud-starter-gateway-server-webflux` (the Boot-4 reactive starter).
