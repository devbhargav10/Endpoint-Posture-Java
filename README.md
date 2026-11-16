# Endpoint Posture & Compliance Platform

A dashboard and API for endpoint posture, Cisco ISE session visibility, and operator-approved ISE actions. Cisco ISE remains the enforcement authority; the platform does not automatically enforce from a collected result.

## Architecture

```text
Cisco ISE ──TLS──► Linux Spring Boot API ──► PostgreSQL 16
                         ▲      ▲
                         │      └── Next.js dashboard (via API routes)
                         │ HTTPS, enrolled worker identity, leases and receipts
                   Windows Java worker service
                         │
                         └── PowerShell collection remains disabled until secure
                             endpoint-credential delivery is designed and accepted
```

The Linux backend profile does not launch `powershell.exe` or run the local worker pool. Windows workers are a separate Java 21 service using individually enrolled, revocable credentials and DPAPI-protected local state. Do not enable remote PowerShell collection: the endpoint credential delivery channel is not implemented.

## Components

| Component | Stack | Current state |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security/JWT, JPA, Flyway | Worker lifecycle, lease fencing, result receipts/retries, job recovery, bounded APIs, ISE TLS/XML hardening and durable ISE actions are integrated. Migrations validate through V32. |
| Database | PostgreSQL 16 | Flyway applies 29 ordered migrations through V32 (V5–V7 are intentionally absent). |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind, Vitest | Dashboard, login, role-aware navigation and API client. Run lint, typecheck, tests and build as documented below. |
| Worker | Windows Java 21 runtime | Separately packaged; service installation and real Windows SCM/DPAPI acceptance remain environment-specific. |
| Infrastructure | Terraform, AWS provider 6.62 | VPC/subnet/security-group and private RDS foundation only. It does not create compute, ALB/TLS ingress, runner fleet, IAM workload roles, VPN/site routes, or monitoring. |

## Run locally with Docker Compose

PowerShell example that generates local-only values in the current shell (nothing is written to source control):

```powershell
function New-LocalSecret {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  [Convert]::ToBase64String($bytes)
}
$env:POSTGRES_PASSWORD = New-LocalSecret
$env:JWT_SECRET = New-LocalSecret
$env:SEED_ADMIN_PASSWORD = New-LocalSecret
$env:POSTURE_API_KEY = New-LocalSecret
docker compose up --build -d
```

Open the dashboard at `http://localhost:3000`, the backend at `http://localhost:8090`, and Adminer at `http://localhost:8081`. The Compose backend uses the `linux` Spring profile and runs database migrations on startup. The Windows worker is not started by Compose. `docker compose down` keeps the named database volume; do not use `-v` if you need to preserve local database data.

For a Windows-hosted backend development profile, see `backend/README.md` and `Doc/BACKEND_BEGINNER_GUIDE.md`. Do not place real secrets in application YAML, shell history, Terraform examples, or Git.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL JDBC connection and application credentials. Compose uses service host `postgres`; local defaults are in `backend/src/main/resources/application.yml`. |
| `JWT_SECRET` | Signing key for dashboard tokens; use a random value with at least 256 bits of entropy. |
| `SEED_ADMIN_USER`, `SEED_ADMIN_PASSWORD` | Initial admin seed; use an environment-specific password and rotate after setup. |
| `POSTURE_API_KEY` | Agent ingestion credential. Worker keys are enrolled and rotated individually through the admin API. |
| `ISE_BASE_URL`, `ISE_USERNAME`, `ISE_PASSWORD` | Cisco ISE API settings. `ISE_VERIFY_TLS` defaults to `true`; provide the approved CA truststore through `ISE_TRUSTSTORE_PATH`, `ISE_TRUSTSTORE_PASSWORD`, and `ISE_TRUSTSTORE_TYPE`. |
| `APP_SCHEDULING_ENABLED` | Background schedule switch; defaults to `true` outside the test configuration. |
| `WORKER_AUTH_IP_*`, `WORKER_UNAUTH_IP_*`, `WORKER_RATE_*` | Per-backend-replica pre-authentication and worker rate limits. Apply additional shared limits at the production ingress. |
| `BACKEND_URL` | Frontend Docker build argument for Next.js API rewrites; Compose sets `http://backend:8090` on its private network. |

## Verification commands

```powershell
# Backend, including PostgreSQL Testcontainers; Docker must be available.
cd backend
mvn -B clean verify "-Duser.timezone=UTC"

# Frontend
cd ..\frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build

# Windows worker runtime
cd ..\worker-runtime
mvn -B clean verify "-Duser.timezone=UTC"

# Terraform (Terraform 1.10+; validation only, no apply)
cd ..
terraform fmt -check -recursive terraform
terraform -chdir=terraform init -backend=false
terraform -chdir=terraform validate
```

GitHub Actions runs backend, Windows worker, frontend and Terraform checks. CI builds both Docker images; it does not publish them to a registry. No AWS resources are deployed by these checks.

## Infrastructure and production acceptance

Terraform examples are placeholders, not deployment-approved account, CIDR, sizing or region values. The foundation creates a two-AZ VPC and private encrypted PostgreSQL RDS. It deliberately does not deploy compute or the network paths required for ISE and endpoint sites. Egress remains broad until destinations and VPC endpoints are decided. Terraform state requires a separately bootstrapped encrypted, access-restricted S3 backend. The API database user, application secrets, IAM roles, TLS edge and operations monitoring still need approved designs.

Before production, validate the deployed ISE certificate chain and hostname, perform Windows SCM/DPAPI restart and worker result-retry acceptance, confirm ingress limits and observability, and define backup/restore and secret rotation. See `Doc/BACKEND_RELEASE_READINESS.md`, `deploy/WORKER-DEPLOYMENT.md`, `terraform/README.md`, `Doc/PRODUCTION_CHECKLIST.md`, and `Doc/PROJECT_ARCHITECTURE.md`.

## Documentation

- `Doc/BACKEND_BEGINNER_GUIDE.md` — API/backend setup and behavior.
- `Doc/DATABASE_BEGINNER_GUIDE.md` — schema and migrations.
- `Doc/FRONTEND_BEGINNER_GUIDE.md` — dashboard setup and frontend structure.
- `Doc/PROJECT_ARCHITECTURE.md` — system and trust boundaries.
- `Doc/BACKEND_RELEASE_READINESS.md` — test evidence, limitations and acceptance gates.
- `deploy/WORKER-DEPLOYMENT.md` — Windows service staging, identity, ACL, DPAPI and acceptance.
- `terraform/README.md` — infrastructure scope, local validation and deployment prerequisites.