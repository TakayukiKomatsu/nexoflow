# SRM Credit Engine

SRM Credit Engine is a modular-monolith exercise for pricing and settling BRL/USD credit receivables. The source requirements are in [`docs/README_case_dev_srm.md`](docs/README_case_dev_srm.md); the canonical implementation sequence is the [12-prompt SDD suite](docs/sdd/README.md).

## Current status

**Implemented locally:** Java 21/Spring Boot modular API and React/TypeScript operator SPA; effective reference rates and resilient deterministic mock FX; exact decimal strategy pricing; immutable 15-minute Pricing Quote snapshots; atomic, idempotent Settlement with PostgreSQL concurrency and rollback coverage; immutable whole-Settlement Reversal; stable signed Settlement Ledger Entries; local JWT role authorization; structured logs, bounded metrics, health probes, Compose runtime, and CI security gates.

**Executable evidence:** ten Cucumber scenarios cover authorization, FX resilience, pricing, quote immutability, settlement concurrency/rollback, reversal/ledger behavior, and observability (`make test-api-features`, report: `backend/build/reports/cucumber.json`). Playwright E2E-001 drives the financial operator path through a real browser, backend, and PostgreSQL (`make test-ui-features`, report: `frontend/playwright-report/index.html`). `make explain-statements-representative` captures a PostgreSQL 16 plan for a representative 10,000-row Settlement dataset at `docs/evidence/reporting-explain.txt`; this is not production-scale or 1M-transactions/minute proof. `make release-check` aggregates the local release gates.

**Deliberate limitations:** local signed JWT/BCrypt rather than external OIDC, deterministic mock rather than real market FX data, no refresh tokens, no FX triangulation, no product-type or user-management API, and no Kubernetes/Terraform/microservice runtime. Remote pull requests, publication, tags, and releases remain authorization-gated and have not been executed.

## Run locally

Prerequisites: Java 21, Node 26, and a PostgreSQL database. Copy the environment template and replace its placeholder values:

```bash
cp .env.example .env
set -a; source .env; set +a
./scripts/with-java21.sh ./backend/gradlew -p backend bootRun
npm --prefix frontend run dev
```

The backend requires `SRM_DB_URL`, `SRM_DB_USERNAME`, `SRM_DB_PASSWORD`, and an `SRM_JWT_SECRET` of at least 32 bytes. With the opt-in `dev` profile, `SRM_DEV_OPERATOR_EMAIL`/`SRM_DEV_OPERATOR_PASSWORD` and `SRM_DEV_ADMIN_EMAIL`/`SRM_DEV_ADMIN_PASSWORD` independently seed local `OPERATOR` and `ADMIN` accounts. Blank pairs are skipped; production and test profiles never seed credentials. Test configuration supplies isolated H2 settings only under `src/test/resources`.

## Verify

```bash
make verify-fast       # local unit, hook, frontend-quality, architecture, and CI-contract checks
make release-check     # complete local acceptance and release aggregate; requires Docker
```

Focused evidence commands are documented in the [reviewer runbook](docs/RUNBOOK.md). `make release-check` builds both applications, exercises PostgreSQL/Testcontainers and Compose, runs Cucumber and Playwright, captures representative SQL evidence, executes security/license/SBOM gates, validates documentation/traceability, and proves the disposable crisis/revert workflow.

## Reviewer Compose runtime

The default runtime is a self-contained PostgreSQL, Spring backend, built React frontend, and deterministic mock FX service. It uses an internal network and a named PostgreSQL volume.

```bash
docker compose up --build --wait
make smoke-compose
make fixtures-e2e
make verify-readiness-recovery
docker compose down -v --remove-orphans
```

`/actuator/health/liveness` only checks process liveness. `/actuator/health/readiness` includes PostgreSQL and returns to `UP` after PostgreSQL recovers without restarting the backend. The fixture jobs are one-shot and idempotent; `e2e-fixtures` uses the fixed instant `2030-01-15T12:00:00Z`, while `dev-fixtures` contains no expiry-sensitive data. There is no reset endpoint or production fixture profile. `make test-runtime` and `make verify-compose` are required in CI; on a machine without a working Docker daemon they exit with an explicit `BLOCKED` result rather than treating the checks as passed.

Install local hooks once with `make install-hooks`. The pre-push hook runs the same backend and frontend unit suite as `make verify-unit`.

## Architecture and delivery evidence

- [Reviewer runbook](docs/RUNBOOK.md) and [permission matrix](docs/PERMISSION_MATRIX.md)
- [Requirement traceability](docs/REQUIREMENT_TRACEABILITY.md)
- [Domain glossary](docs/CONTEXT.md) and [Git workflow](docs/GIT_WORKFLOW.md)
- [ER diagram](docs/architecture/er-diagram.mmd)
- [C4 context](docs/architecture/c4-context.mmd) and [C4 container](docs/architecture/c4-container.mmd)
- [Architecture decisions](docs/adr)
- [Cucumber scenarios](backend/src/integrationTest/resources/features/srm_acceptance.feature) and generated `backend/build/reports/cucumber.json`
- [Playwright critical path](frontend/e2e/operator-critical-path.spec.ts) and generated `frontend/playwright-report/`
- [Representative PostgreSQL plan](docs/evidence/reporting-explain.txt)
- Generated security evidence under `build/security/`: Gitleaks, Trivy filesystem/secret/runtime-image reports, immutable image digests, and CycloneDX SBOM
- [AI usage record](AI_USAGE.md) and [human/tooling controls](HT_USAGE.md)
