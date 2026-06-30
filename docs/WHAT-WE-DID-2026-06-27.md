# CampusConnect — What We Did (27 June 2026)

A plain-English record of everything done in this session, and **how the app went live on the internet**.

---

## Part 1 — Making the App Look Great (UI/UX)

We polished the whole app, **portal by portal**. All of this was front-end (how it looks), no business logic changed, and every change kept the **201 automated tests green**.

### Login / Auth
- Added a **Reset** button on Login and Create-Account.
- Added **mandatory-field validation** with clear "X is required" messages.
- Replaced the flat blue login background with a **rich, per-portal mesh gradient** + floating role icons.
- Added a **green success toast** (top-right) after sign-in.
- Added a **password show/hide eye** to every password box.

### Student / Recruiter / Admin portals
- **Coloured sidebar** with a gradient "active" pill + icons.
- **Textured, tinted page backgrounds** (no more plain white).
- **Coloured top-edge accents** on cards; **dashboards filled** with useful content (journey, deadlines, activity, charts).
- **Friendly empty states** (e.g. "No drives yet") instead of blank pages.
- **API messages shown as green/red toasts** (top-right) on errors.
- Profile page: **mandatory markers** + a **drag-&-drop résumé box**.
- Recruiter: create-drive validation, colour-coded Pass/Fail/Absent buttons.
- Admin: an attractive **"Placement season" lock/unlock** control; report **progress bars**.

### Other fixes
- **Reports CSV bug fixed** — the "joining date" column was showing a full machine timestamp (`2026-10-21T06:55:43.641Z`); now it's a clean date (`2026-10-21`).
- Added **faded per-portal background illustrations** (graduation cap / briefcase / shield) at low opacity.

All of this was **committed to GitHub** and **CI ran green** (build → test → publish container images).

---

## Part 2 — Putting the App LIVE on the Internet (the big one)

**Goal:** take the app (which only ran on your laptop) and make it reachable by anyone at a public web address.

**The challenge:** the app is **5 pieces** that must all run together — 3 back-end services (student, recruiter, admin), an API gateway, and the website (front-end) — plus a database. That needs a real server.

### Step 1 — Pick a server (and the constraint)
- The clean choice was **Oracle's free 24 GB server**, but it needs a **Visa/Mastercard** for sign-up, and you only have **RuPay** → blocked.
- So we used the **AWS free-tier server (1 GB RAM)** instead — much smaller, so we had to slim everything down.

### Step 2 — The database (MongoDB Atlas, free)
- Created a **free MongoDB Atlas** database in the cloud (no card needed).
- **Copied all your demo data** (logins, students, drives, offers — 379 records) from your laptop into it.

### Step 3 — Build the "deploy recipe"
- Wrote `docker-compose.aws.yml` — the recipe that tells the server how to run all 5 pieces.
- Built a **front-end image** (a small web server, nginx, that serves the website and forwards `/api` calls to the gateway).
- Changed CI to build **Intel (amd64)** images, because AWS free boxes are Intel (Oracle's were ARM).
- Wrote `deploy/setup-aws.sh` — a script that installs Docker and sets up extra memory (swap).

### Step 4 — Launch the AWS server + connect
- Launched an **Ubuntu** server on AWS, opened ports for SSH (22) and web (80), downloaded the access key.
- **Logged into the server** from your laptop with `ssh`.
- On the server: cloned the project, ran the setup script, created the config file (`.env`).

### Step 5 — Start it… and fix the problems we hit
Getting it running took **debugging three real problems** (this is normal!):

1. **The website wouldn't open from outside** → AWS was **blocking port 80**. Fix: added an "Allow HTTP" rule in the AWS Security Group.

2. **Login failed — couldn't reach the cloud database** → the Java services **could not complete the secure (TLS) connection to MongoDB Atlas** on this particular server (a low-level handshake error). We tried several fixes (memory tuning, TLS version, network MTU) — none worked.
   → **Solution:** stop using Atlas, and run **MongoDB *on the server itself*** (no secure handshake needed). We packaged your demo data into the project so it auto-loads on first start.

3. **The database wouldn't start** → the server's **operating system was too new** (Ubuntu 26.04, Linux kernel 7.0), and **MongoDB 8 refuses to run on it**.
   → **Solution:** used **MongoDB 6**, which runs fine.

### Step 6 — Success ✅
All 5 services reported **healthy**, the data loaded, and the app went **live** at `http://3.94.103.178`.

### Step 7 — Added a real domain + free HTTPS 🔒
A raw IP over `http://` is ugly and warns "Not secure" (especially on phones). So we added:
- **A free domain** from **DuckDNS** — `shankar-campusconnect.duckdns.org` → pointed at the server's IP.
- **Caddy** — a tiny web server placed in *front* that automatically gets a **free SSL certificate** from Let's Encrypt and serves the site over **HTTPS**. (One gotcha: the `DOMAIN` in the config must be *your* real domain — a leftover example value made the first cert attempt fail.)

The app is now live with a padlock:

# 🌍 https://shankar-campusconnect.duckdns.org

**Login:** `admin@demo-tech.edu` / `Admin@12345` / College: `demo-tech`

---

## Part 3 — How It All Fits Together

```
   Your browser / phone
        │  https://shankar-campusconnect.duckdns.org
        ▼
   ┌─────────────┐
   │   Caddy     │  ← free HTTPS (SSL certificate), ports 80 + 443
   └─────────────┘
        │
        ▼
   ┌─────────────┐
   │  frontend   │  (nginx)  ← serves the website
   │             │  ── /api ──┐
   └─────────────┘            ▼
                       ┌──────────────┐
                       │ api-gateway  │  ← routes /api/... to the right service
                       └──────────────┘
                          │     │     │
                ┌─────────┘     │     └──────────┐
                ▼               ▼                ▼
        ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
        │ student-svc  │ │ recruiter-svc│ │  admin-svc   │
        └──────────────┘ └──────────────┘ └──────────────┘
                \              │              /
                 \             ▼             /
                  └────►  ┌──────────┐  ◄───┘
                          │ MongoDB  │  (runs on the same server)
                          └──────────┘
```
All of these run as **Docker containers** on the one AWS server.

---

## Part 4 — How to Operate It

- **Keep it live:** don't *stop/terminate* the AWS instance. Free tier covers one instance running 24/7 for 12 months.
- **It survives reboots:** containers auto-restart, and the database data is stored on a disk volume. After a reboot: `sudo docker compose -f docker-compose.aws.yml up -d`.
- **See status:** `sudo docker compose -f docker-compose.aws.yml ps`
- **See logs:** `sudo docker compose -f docker-compose.aws.yml logs -f api-gateway`
- **Works everywhere:** open **https://shankar-campusconnect.duckdns.org** on any device (desktop or phone) — padlock and all.
- **DuckDNS tip:** DuckDNS free domains expire if not "touched" for ~30 days. Just log in to duckdns.org occasionally (or set up its auto-update) so the domain stays alive.

### Current limitations (fine for a demo)
- **Résumé upload & offer-PDF download don't work** (file storage was left out to save memory).
- A bit **slow** on the 1 GB box.

---

## Part 5 — Optional Next Steps

1. **Move to Oracle's free 24 GB server later** (if you get a Visa/Mastercard) → full features (file storage, monitoring), much faster — the deploy for it is already built (`docker-compose.prod.yml`).
2. **Re-enable file storage** (MinIO) so résumé upload / offer-PDF work — needs more memory than the 1 GB box has, so best paired with #1.

---

## Files Created/Changed for the Deploy
- `docker-compose.aws.yml` — the slim deploy recipe (Caddy + 5 services + on-box MongoDB 6)
- `frontend/Dockerfile`, `frontend/deploy-nginx.conf` — the front-end web-server image
- `deploy/Caddyfile` — Caddy config for free HTTPS in front of the app
- `.env.aws.example` — config template (`JWT_SECRET`, `GHCR_OWNER`, `DOMAIN`, `ACME_EMAIL`)
- `deploy/setup-aws.sh` — server setup script (Docker + swap)
- `deploy/seed/campusconnect.archive.gz` — your demo data, auto-loaded on first start
- `.github/workflows/ci.yml` — now builds Intel (amd64) images + the front-end image
- `admin-service/.../reports/ReportService.java` — the CSV joining-date fix
- `docs/architecture-aws.svg` / `.png` — the architecture diagram
