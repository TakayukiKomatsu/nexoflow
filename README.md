# SRM Credit Engine

SRM Credit Engine is a modular-monolith exercise for pricing and settling BRL/USD credit receivables. The source requirements are in [`docs/README_case_dev_srm.md`](docs/README_case_dev_srm.md); the canonical implementation sequence is the [12-prompt SDD suite](docs/sdd/README.md).

## Current status

**Implemented:** Java 21/Spring Boot and React scaffolds, Flyway migrations for identity and reference data, correlated RFC 9457 errors, signed 15-minute JWT login and verification, role denial, login rate limiting, money/rate/FX value boundaries, local Git hooks, CI, ADRs, ER source, and quality checks.

**Known gaps:** PostgreSQL/Testcontainers and Docker Compose verification, reference-data APIs and effective-rate selection, pricing strategies and quotes, settlement/reporting, the operator UI, observability, E2E/security release evidence, and the remaining SDD prompts. H2 is test-only; it is not PostgreSQL compatibility evidence.

## Run locally

Prerequisites: Java 21, Node 26, and a PostgreSQL database. Copy the environment template and replace its placeholder values:

```bash
cp .env.example .env
set -a; source .env; set +a
./scripts/with-java21.sh ./backend/gradlew -p backend bootRun
npm --prefix frontend run dev
```

The backend requires `SRM_DB_URL`, `SRM_DB_USERNAME`, `SRM_DB_PASSWORD`, and an `SRM_JWT_SECRET` of at least 32 bytes. With the opt-in `dev` profile, `SRM_DEV_OPERATOR_EMAIL` and `SRM_DEV_OPERATOR_PASSWORD` seed one local `OPERATOR`; production and test profiles never seed credentials. Test configuration supplies isolated H2 settings only under `src/test/resources`.

## Verify

```bash
make verify-fast
make build
```

Install local hooks once with `make install-hooks`. The pre-push hook runs the same backend and frontend unit suite as `make verify-unit`.

## Architecture and delivery evidence

- [Domain glossary](docs/CONTEXT.md)
- [Git workflow](docs/GIT_WORKFLOW.md)
- [Requirement traceability](docs/REQUIREMENT_TRACEABILITY.md)
- [ER diagram](docs/architecture/er-diagram.mmd)
- [C4 context](docs/architecture/c4-context.mmd) and [C4 container](docs/architecture/c4-container.mmd)
- [Architecture decisions](docs/adr)
- [AI usage record](AI_USAGE.md)
