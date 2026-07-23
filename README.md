# SRM Credit Engine

SRM Credit Engine is a modular-monolith exercise for pricing and settling BRL/USD credit receivables. The source requirements are in [`docs/README_case_dev_srm.md`](docs/README_case_dev_srm.md); the canonical implementation sequence is the [12-prompt SDD suite](docs/sdd/README.md).

## Current status

**Implemented locally:** Java 21/Spring Boot modular API and React/TypeScript operator SPA; effective reference rates and resilient deterministic mock FX; exact decimal strategy pricing; immutable 15-minute Pricing Quote snapshots; atomic, idempotent Settlement with PostgreSQL concurrency and rollback coverage; immutable whole-Settlement Reversal; stable signed Settlement Ledger Entries; local JWT role authorization; structured logs, bounded metrics, health probes, Compose runtime, and CI security gates.

**Executable evidence:** twelve Cucumber scenarios cover authorization, FX resilience, pricing, quote immutability, settlement concurrency/rollback, reversal/ledger behavior, and observability (`make test-api-features`, report: `backend/build/reports/cucumber.json`). Playwright E2E-001 drives the financial operator path through a real browser, backend, and PostgreSQL (`make test-ui-features`, report: `frontend/playwright-report/index.html`). `make explain-statements-representative` captures a PostgreSQL 16 plan for a representative 10,000-row Settlement dataset at `docs/evidence/reporting-explain.txt`; this is not production-scale or 1M-transactions/minute proof. `make release-check` aggregates the local release gates.

**Deliberate limitations:** local signed JWT/BCrypt rather than external OIDC, deterministic mock rather than real market FX data, no refresh tokens, no FX triangulation, no product-type or user-management API, and no Kubernetes/Terraform/microservice runtime. No remote is configured, so hosted pull requests, publication, protected-branch checks, and a remote release remain unverified and authorization-gated. An existing local annotated `v1.0.0` tag points to `af898ef`; it predates the current remediation and is historical local evidence, not proof that this HEAD was reviewed, published, or released. It has not been moved or reused; any authorized future release must select a new version and exact reviewed SHA.

## Run locally

The recommended reviewer path requires Docker with Compose v2. Native development requires Java 21, Node 26 with npm, PostgreSQL 16 (database `srm_credit_engine` created and reachable), and two terminals. Copy the environment template, replace every placeholder, and install the locked frontend dependencies once:

```bash
cp .env.example .env
npm --prefix frontend ci
set -a; source .env; set +a
./scripts/with-java21.sh ./backend/gradlew -p backend bootRun
# In a second terminal:
set -a; source .env; set +a
npm --prefix frontend run dev
```

The backend requires `SRM_DB_URL`, `SRM_DB_USERNAME`, `SRM_DB_PASSWORD`, and an `SRM_JWT_SECRET` of at least 32 random bytes. With the opt-in `dev` profile, `SRM_DEV_OPERATOR_EMAIL`/`SRM_DEV_OPERATOR_PASSWORD` and `SRM_DEV_ADMIN_EMAIL`/`SRM_DEV_ADMIN_PASSWORD` independently seed local `OPERATOR` and `ADMIN` accounts. Blank pairs are skipped; production and test profiles never seed credentials. The Vite development server forwards `/api` to `VITE_API_PROXY_TARGET` (default `http://127.0.0.1:8080`); this proxy is for native development, while Compose uses nginx. Test configuration supplies isolated H2 settings only under `src/test/resources`.

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

## GitHub Flow rationale

This repository uses GitHub Flow: one releasable `main` plus short-lived
`feature/<topic>` or `fix/<topic>` branches. It fits a small, time-boxed delivery
better than Git Flow because there are no long-lived release/develop branches to
synchronize, while pull-request review and required checks still protect the
integration branch. Commits use Conventional Commits; unpublished fixups are
autosquashed before first push, and reviewed/shared branches are never rebased or
force-pushed. `make test-local-collaboration-evidence` proves the branch,
PR-description, interactive autosquash, `range-diff`, and fast-forward mechanics
inside a remote-free disposable clone. It does not claim a hosted PR or review.
The detailed policy and crisis procedure are in the [Git workflow](docs/GIT_WORKFLOW.md).

## Proposed evolution to 1M transactions/minute

This section is a **Proposed** capacity design, not an implemented topology and
not production-capacity evidence. The repository has only a modular monolith,
one PostgreSQL instance, deterministic local tests, and a representative
10,000-row query plan. A production decision would require load, failure, restore,
and cost tests on the intended infrastructure.

### Capacity model and partitioning

One million transactions/minute is 16,667 transactions/second sustained. Design
for a 2× peak of 33,334 transactions/second. At an assumed 2 KiB accepted command,
ingress is about 65 MiB/s at peak before protocol and replication overhead. With
an assumed four Receivables and eight authoritative row writes per Settlement,
the peak is roughly 267,000 row writes/second. These are sizing assumptions, not
measurements.

Start with 128 log partitions (about 261 peak commands/second per partition) and
64 PostgreSQL write shards (about 4,200 assumed row writes/second per shard),
then validate p99 latency, replication/WAL bandwidth, hot-key skew, and storage
headroom. Partition by a stable tenant/assignor ownership key; route all commands
for one Receivable to one partition. Time sub-partitioning supports retention,
but must not split a single Settlement consistency boundary. Autoscaling begins
before 70% sustained CPU, connection, log-lag, or storage utilization.

### Ordering, consistency, and idempotent consumers

The proposed ingress authenticates, validates size/shape, and claims the
actor/operation/idempotency key before durably accepting a command. Transport is
at-least-once. Partition ordering plus a consumer inbox/deduplication record makes
the business effect exactly-once: the same key and request hash returns the stored
result, while a different hash conflicts. Each worker commits Settlement rows and
a transactional outbox in the same shard transaction. Projectors checkpoint
event ID and version, tolerate duplicates, quarantine poison messages, and support
rate-limited replay and independent reconciliation. Settlement and quote
consumption remain strongly consistent within a partition; statement/search
projections are eventually consistent with a proposed 30-second lag objective.

Versioned Base Rates, Risk Spreads, and FX observations may be cached by immutable
version key with bounded TTL and event-driven invalidation. A cache miss reads the
authoritative version store; Settlement state and idempotency results are never
made authoritative in a cache.

### SLO, disaster recovery, and operations

Proposed starting objectives are 99.95% monthly durable-command acceptance,
p99 acceptance latency below 250 ms, 99% Settlement completion below 5 seconds,
and 99.9% statement availability with projection lag below 30 seconds. Alert on
error-budget burn, partition lag/skew, idempotency conflicts, reconciliation
differences, database saturation, and outbox age—not only host health.

For disaster recovery, target RPO ≤ 1 minute for accepted commands and RTO ≤ 30
minutes for a regional loss using cross-region log replication, PostgreSQL WAL
archival/replicas, encrypted backups, and infrastructure rebuilt from reviewed
configuration. Quarterly restore/failover drills must prove those targets; until
then they remain objectives. Recovery promotes one writer per shard, replays from
the last reconciled checkpoint, and verifies financial totals before reopening
writes.

### Ownership and extraction triggers

Platform owns ingress/log/schema governance; a Settlement team owns command
processing and shard integrity; Reporting owns projections/query SLOs; Treasury
owns rate provenance; Security owns identity/key policy. Extract a Module only
when it has a named owning team/on-call, independent SLO or release cadence, and
measured contention or scaling benefit. Sustained >70% resource use, unacceptable
release coupling, or a regulatory isolation boundary are valid triggers. Before
extraction, require versioned contracts, outbox/inbox semantics, backfill and
reconciliation, failure-mode tests, dashboards/runbooks, and an incremental
rollback plan. The [proposed scale diagram](docs/architecture/scale-evolution.mmd)
shows this evolution without claiming it exists.

## Architecture and delivery evidence

- [Reviewer runbook](docs/RUNBOOK.md) and [permission matrix](docs/PERMISSION_MATRIX.md)
- [Requirement traceability](docs/REQUIREMENT_TRACEABILITY.md)
- [Domain glossary](docs/CONTEXT.md) and [Git workflow](docs/GIT_WORKFLOW.md)
- [ER diagram](docs/architecture/er-diagram.mmd)
- [DDL/schema inventory](docs/architecture/schema-inventory.md) and [API endpoint inventory](docs/architecture/api-endpoints.md)
- [C4 context](docs/architecture/c4-context.mmd) and [C4 container](docs/architecture/c4-container.mmd)
- [Settlement state](docs/architecture/settlement-state.mmd) and [atomic/idempotent sequence](docs/architecture/settlement-sequence.mmd)
- [Architecture decisions](docs/adr)
- [Cucumber scenarios](backend/src/integrationTest/resources/features/srm_acceptance.feature) and generated `backend/build/reports/cucumber.json`
- [Playwright critical path](frontend/e2e/operator-critical-path.spec.ts) and generated `frontend/playwright-report/`
- [Representative PostgreSQL plan](docs/evidence/reporting-explain.txt)
- Generated security evidence under `build/security/`: Gitleaks, Trivy filesystem/secret/runtime-image reports, immutable image digests, and CycloneDX SBOM
- [AI usage record](AI_USAGE.md) and [human/tooling controls](HT_USAGE.md)
