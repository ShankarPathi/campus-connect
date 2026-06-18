# Campus Connect — Deployment Runbook

Production runs the four Spring Boot services + MongoDB + MinIO as containers via
`docker-compose.prod.yml`, on a single Oracle Always-Free **ARM (linux/arm64)** VM. This file covers
**Story 10.1 (containerize + run the stack)**. HTTPS/Caddy/SPA serving is **Story 10.3**; monitoring +
backups are **Story 10.4**; the CI image build/push is **Story 10.2**.

> Dev loop is unchanged: `docker-compose.yml` (Mongo + MinIO + Mailpit) + `mvn spring-boot:run` per
> service + `cd frontend && npm start`. The *prod* stack is the separate `docker-compose.prod.yml`.

## What gets built

| Image | Port (internal) | Notes |
|---|---|---|
| `campus-connect/api-gateway` | 8080 | the **only** published port; routes `/api/**` to the services |
| `campus-connect/student-service` | 8081 | also hosts `/api/platform/**` (tenant provisioning) |
| `campus-connect/recruiter-service` | 8082 | |
| `campus-connect/admin-service` | 8083 | runs the `@Scheduled` jobs (offer-expiry, email-outbox-flush) |

Each is a **multi-stage** build: JDK 21 + Maven compiles the reactor module (`-pl <svc> -am`), then the
jar is copied onto a **JRE 21** runtime that runs as a **non-root** user with
`JAVA_OPTS=-Xmx384m -XX:+UseSerialGC` (architecture §14 — fits the RAM budget).

## 1. Configure secrets

```bash
cp .env.example .env
# then edit .env:
#   JWT_SECRET           -> openssl rand -base64 48   (>= 32 bytes, NOT the dev default)
#   MINIO_ROOT_USER/PASSWORD
#   MAIL_* (Brevo SMTP for prod; Mailpit for local)
```
`.env` is git-ignored. **No real secret is ever committed.**

## 2. Build + run the stack

```bash
docker compose -f docker-compose.prod.yml up -d --build
```
Startup is ordered by healthchecks: Mongo + MinIO become healthy → the bucket is created → the three
role services start and become healthy → the gateway starts last.

## 3. Verify

```bash
docker compose -f docker-compose.prod.yml ps           # all "healthy"
curl -fsS http://localhost:8080/actuator/health        # {"status":"UP"} through the gateway
```

Stop / logs:
```bash
docker compose -f docker-compose.prod.yml logs -f api-gateway
docker compose -f docker-compose.prod.yml down          # add -v to wipe Mongo/MinIO volumes
```

## Building for the ARM VM (linux/arm64)

Building **on** the Oracle VM is native — just run step 2 there. Building **off** the VM (e.g. an x86
laptop or CI) requires cross-build:
```bash
docker buildx build --platform linux/arm64 -f api-gateway/Dockerfile -t campus-connect/api-gateway:latest .
# (repeat per service; build context is always the repo root)
```
In Story 10.2 the CI pipeline builds these for arm64 and pushes them to `ghcr.io`.

## CI/CD pipeline (Story 10.2)

`.github/workflows/ci.yml` runs on every push/PR to `main`:

| Job | What it does | Gate |
|---|---|---|
| **build** | `./mvnw clean verify` — full reactor + Testcontainers Mongo, JaCoCo coverage, optional Sonar | JUnit + **CrossTenantIsolationTest** (asserted to have run) + **JaCoCo coverage** all fail the build |
| **frontend** | Node 22: `npm ci` → `CI=true npm test` (vitest) → `npm run build` | a failing Angular test/build fails CI |
| **dockerfiles** | `hadolint` on the 4 Dockerfiles (`failure-threshold: error`) | a lint error fails CI |
| **publish** | `needs: [build, frontend]`, **push-to-`main` only** — buildx `linux/arm64` build + push of all 4 services to ghcr.io | only runs if the gates above are green |

**Coverage gate (JaCoCo, architecture §13.2 "start 70% → 80%").** Enforced by `jacoco:check` bound to `verify`, per-module **LINE** ratio ≥ `${jacoco.min-coverage}` (root `pom.xml`). **It currently starts at `0.50`** (a conservative floor below the well-tested backend) — **api-gateway is exempt** (`0.00` in its pom; a thin routing module). **Calibrate on the first CI run:** read the run's actual coverage, then raise `jacoco.min-coverage` toward the architecture's **0.70 → 0.80**. Never lower/skip it to make a red run green.

**SonarQube** is wired but **secret-gated** — the `sonar:sonar` step runs only when the repo secrets **`SONAR_TOKEN`** + **`SONAR_HOST_URL`** are set (the CE server isn't deployed until later). JaCoCo is the enforced gate meanwhile.

**Published images** (on push to main): `ghcr.io/<owner>/campus-connect-{api-gateway,student-service,recruiter-service,admin-service}`, tagged `latest` + the commit SHA, built for **`linux/arm64`** (the Oracle VM). Auth uses the workflow's built-in `GITHUB_TOKEN` (no PAT). Pull on the VM with `docker login ghcr.io` (a read PAT or the same token) then `docker pull ghcr.io/<owner>/campus-connect-<svc>:latest`. Story 10.3 switches `docker-compose.prod.yml` from `build:` to these `image:` refs for the actual deploy. **Note the name change:** the dev/build compose uses local `campus-connect/<svc>:latest`, but the published (and deploy-pulled) names are **`ghcr.io/<owner>/campus-connect-<svc>:latest`** (hyphen, registry-prefixed, lowercased owner). 10.3 must use the ghcr form or the VM pulls a name that was never pushed.

## Known issue — Mongo connection property (Spring Boot 4)

Containerizing surfaced a latent config bug. The services' `application.yml` declares the Mongo
connection under **`spring.data.mongodb.uri`** — the **pre-Boot-4** key. **Spring Boot 4 reads the
connection from `spring.mongodb.uri`** instead, so the declared uri is silently ignored and the driver
falls back to its default `localhost:27017` (default DB `test`). On a dev box where Mongo runs on
localhost this "works" by accident (and is why dev data lands in the `test` DB); in a container,
`localhost` has no Mongo and the services can't connect.

**Fix applied here (deploy-layer, no app change):** `docker-compose.prod.yml` sets
**`SPRING_MONGODB_URI`** (the Boot-4 key, via its env form) — this overrides the stale yaml value and the
services connect to `mongodb:27017`. It also sets `MANAGEMENT_HEALTH_MAIL_ENABLED=false` so the external
SMTP health check can't mark a service unhealthy / trigger a restart loop.

**Proper follow-up (a backend fix story, not 10.1):** migrate the app to `spring.mongodb.uri` in all
three services' `application.yml` **and** update the integration tests' `@DynamicPropertySource`
registrations (they still register `spring.data.mongodb.uri`) in the same change — doing only one breaks
the other. Tracked in `deferred-work.md`.

## Notes / boundaries

- **The build needs internet to Maven Central** (the build stage downloads dependencies). It runs in a
  clean container with no corporate `~/.m2/settings.xml`, so it uses Central directly. Works in CI and on
  any internet-connected host.
- **Mongo/MinIO are internal only** (no host ports) — reach them via the gateway. The MinIO console and
  Mongo are exposed (if needed) through Caddy in 10.3, not published here.
- **Emailed verification links** still point at the service's own endpoint; they get repointed at the
  public SPA route in 10.3 (the public origin doesn't exist until the domain is set up).
- **Extension seams** are marked in `docker-compose.prod.yml`: `caddy` (10.3), `prometheus`/`grafana`/
  `uptime-kuma` + `mongo-backup` (10.4).
