# Reviewer runbook

Operational guide for running, verifying, and reviewing the SRM Credit Engine locally. Every command below is copied from [`Makefile`](../Makefile), [`README.md`](../README.md), and `scripts/`; none is invented for this document.

## 1. Run locally

### Compose (recommended reviewer path)

The default reviewer runtime is `compose.yaml`: PostgreSQL, the Spring backend, the built React frontend, and a deterministic mock FX service on an internal-only Docker network.

```bash
cp .env.example .env
set -a; source .env; set +a
docker compose up --build --wait
```

`backend`, `frontend`, `mock-fx`, and `postgres` each carry a `healthcheck`; `--wait` blocks until all report healthy. Tear down with:

```bash
docker compose down -v --remove-orphans
```

### Native (backend + frontend processes)

Prerequisites: Java 21, Node 26 with npm, and a reachable PostgreSQL 16 instance
with database `srm_credit_engine` already created. Use two terminals because
`bootRun` remains in the foreground.

```bash
cp .env.example .env
npm --prefix frontend ci
set -a; source .env; set +a
./scripts/with-java21.sh ./backend/gradlew -p backend bootRun
# In a second terminal:
set -a; source .env; set +a
npm --prefix frontend run dev
```

Required environment variables (see `.env.example`): `SRM_DB_URL`, `SRM_DB_USERNAME`, `SRM_DB_PASSWORD`, `SRM_JWT_SECRET` (≥32 random bytes). With `SPRING_PROFILES_ACTIVE=dev`, the `SRM_DEV_OPERATOR_*` and `SRM_DEV_ADMIN_*` pairs independently seed local `OPERATOR` and `ADMIN` users through `DevelopmentOperatorSeeder` (`backend/src/main/java/com/srm/creditengine/identity/infrastructure/DevelopmentOperatorSeeder.java`). A blank pair is skipped independently. The seeder never runs outside `dev`; `test` and production profiles seed no credentials. `VITE_BACKEND_ORIGIN` defaults to `http://127.0.0.1:8080` and is used only by the native Vite proxy; Compose routes `/api` through nginx.

### Upgrading a V1–V22 database through V23

V1–V22 stored application `Instant` values as PostgreSQL `timestamp without
time zone`, using the legacy JVM/default zone as the wall-time encoding. Before
the first V23 startup:

1. Stop all writers, take a recoverable backup, and reserve a maintenance
   window. V23 changes sixteen timestamp column types with heavyweight table
   locks and may rewrite populated tables; it is not an online rolling upgrade.
2. Identify the single IANA zone used by the legacy application writers. V23
   selects `-Dsrm.migration.v23.legacy-time-zone=<zone>` first,
   `SRM_MIGRATION_V23_LEGACY_TIME_ZONE=<zone>` second, and the upgrade JVM
   default last. Set an override whenever the upgrade host's zone differs from
   the legacy writers, for example
   `SRM_MIGRATION_V23_LEGACY_TIME_ZONE=America/Sao_Paulo`.
3. Do not run V23 over rows written in multiple legacy zones. Their provenance
   is ambiguous and needs an explicit reconciliation migration. An invalid or
   PostgreSQL-unsupported override fails migration rather than falling back.

The six deterministic reference-rate rows with Flyway-owned stable IDs retain
their authored UTC literals. After V23, all persisted instants use
`timestamp with time zone` and no longer depend on the session zone.

### Incident rollback and restore

This repository does not control production traffic, deployments, or backup
infrastructure. Before a release, the operator must map the following steps to
the hosting platform, name the incident owner, and record the image digest,
database backup identifier, and rollback decision.

1. Stop new writes at the ingress or deployment layer. Preserve backend,
   PostgreSQL, and correlation-ID logs before replacing any runtime.
2. Confirm that the latest pre-release PostgreSQL backup has been restored into
   an isolated database and checked before treating it as recoverable.
3. Choose one recovery path:
   - For an application-only defect, deploy the last known-good immutable image
     against the current compatible schema.
   - If V23 started and its schema must be rolled back, keep writers stopped and
     restore the verified pre-V23 backup. Never hand-edit Flyway history or run
     an improvised reverse migration.
   - For suspected data corruption, restore into an isolated database first;
     do not overwrite the affected database until the incident owner has
     preserved evidence and approved the restore point.
4. Check `/actuator/health/liveness`, then
   `/actuator/health/readiness`. Readiness must report `UP` with PostgreSQL
   reachable before any traffic resumes.
5. Confirm the expected Flyway version, find the relevant `audit_events` by
   correlation ID, follow each event's `target_id` to the settlement or
   reversal ledger, and run a read-only reporting query for the affected
   assignor or receivable. Do not use settlement, reversal, fixture, or reset
   endpoints as health checks.
6. Re-enable writers gradually, monitor error codes and bounded metrics, and
   retain the incident timeline, backup/restore evidence, deployed digest, and
   validation results.

`docker compose down -v` is only the local reset command described in
[Seeding and resetting fixtures](#5-seeding-and-resetting-fixtures); it is not
a production rollback mechanism.

## 2. Verification suite

Run in this order; each target maps directly to a `Makefile` recipe.

| Command | What it runs or proves | Evidence |
| --- | --- | --- |
| `make verify-fast` | Hook contracts, backend/frontend units, frontend quality, architecture docs, CI workflow validation, and the mutation-sensitive reporting-evidence contract | Backend/frontend test reports and reporting contract output |
| `make test-runtime` | Runtime/API/migration tests plus PostgreSQL Testcontainers integrations, including settlement concurrency and rollback | `backend/build/reports/tests/` and `backend/build/reports/tests/integrationTest/` |
| `make verify` | `verify-fast` plus credential/identifier log-redaction checks | Command result |
| `make build` | Backend Gradle build and frontend production build | `backend/build/`, `frontend/dist/` |
| `make verify-compose` | Clean Compose lifecycle: full-stack smoke, deterministic fixtures, PostgreSQL readiness loss/recovery, and authenticated bounded metrics inspection | Command result; stack is removed on exit |
| `make test-api-features` | Twelve executable Cucumber scenarios against Spring and PostgreSQL Testcontainers | `backend/build/reports/cucumber.json`, `backend/build/reports/cucumber.html` |
| `make test-ui-features` | Playwright E2E-001 against a real browser, backend, and PostgreSQL | `frontend/playwright-report/`, `frontend/test-results/` |
| `make e2e-fixed` | Deterministic fixture-backed browser path | Playwright report and Compose output |
| `make explain-statements-representative` | Boots the real backend/Flyway chain, seeds 10,000 representative rows, and runs selective assignor, asset-currency, settlement-currency, product-type, and combined plans from the production SQL template; not a production-scale benchmark | `docs/evidence/reporting-explain.txt` |
| `make license-check` | Backend and frontend production dependency allowlists | `backend/build/reports/dependency-license/`, `frontend/license-report.json` |
| `make security-scan` | Full-history/content Gitleaks; Trivy filesystem, secrets, and runtime images; immutable image digests; CycloneDX SBOM; license gate | `build/security/` |
| `make validate-docs` | Links, required README architecture text, migration/schema consistency, OpenAPI reachability, and prohibited-claim checks | Command result |
| `make validate-traceability` | Every stable SDD scenario ID resolves to an exact matrix row and executable artifact | Command result |
| `make release-check` | Aggregate of local quality, log-redaction, build, runtime, acceptance, performance-evidence, security, docs, and traceability gates | All evidence above |

Docker-dependent targets exit with explicit `BLOCKED` rather than a false pass when Docker is unavailable. CodeQL is a separate pinned GitHub Actions job; it is not represented as a local scan.

Individual Compose checks can be run after `docker compose up --build --wait`:

```bash
make smoke-compose
make fixtures-e2e
make verify-readiness-recovery
make inspect-observability
```

Recommended reviewer sequence:

```bash
make release-check
```

For a focused review, run the relevant target from the table and inspect its named artifact. Do not infer a pass from an artifact left by an older run; pair it with the current command result.

## 3. Review gates

Gates as defined in [`docs/sdd/README.md`](sdd/README.md). Do not cross a red financial-integrity or security gate.

| Gate | Scope | Prompts |
| --- | --- | --- |
| 1. Foundation | Repository, architecture docs, runtime skeleton | 01–02 |
| 2. Core backend | Identity/authorization, reference rates, assignors/receivables, pricing/quotes | 03–04 |
| 3. Financial integrity and ledger | Settlement preview/atomicity, reversal, audit ledger | 05–06 |
| 4. Operator UI | Frontend auth/simulation, preview/settlement/reversal UI | 07–08 |
| 5. Operations and release readiness | Observability, E2E/security evidence, this reviewer documentation | 09–11 |
| 6. Authorization-gated publication | Push/PR/tag/release, gated by explicit human authorization | 12 |

Gates 1–5 have executable local or CI evidence. Cucumber covers the stable backend scenarios in `backend/src/integrationTest/resources/features/srm_acceptance.feature`; Playwright E2E-001 covers the browser financial path; `frontend/src/a11y.test.tsx` checks accessibility; real PostgreSQL integrations cover settlement concurrency and rollback; the representative query plan is persisted under `docs/evidence/`; and CI pins CodeQL, Gitleaks, Trivy, Syft/SBOM, and license gates. Gate 6 remains intentionally unexecuted: remote collaboration, publication, tags, and releases require explicit human authorization.

## 4. Interpreting failures

- **`make test-runtime` / `make verify-compose` report `BLOCKED`**: Docker daemon is not running or not reachable. This is a distinct outcome from `passed` — per the SDD global contract, a blocked prerequisite must never be reported as passed.
- **Compose service fails its healthcheck** (`docker compose up --wait` hangs or errors): inspect with `docker compose logs <service>`. `backend` health depends on `/actuator/health/readiness`, which itself depends on PostgreSQL; `frontend` depends on `backend` being healthy first.
- **`/actuator/health/readiness` returns `503`**: PostgreSQL is unreachable. `/actuator/health/liveness` only checks process liveness and stays `UP` in this case — this is deliberate, so Compose/orchestrators do not restart a backend that only needs PostgreSQL to recover. `scripts/verify-readiness-recovery.sh` exercises this exact behavior.
- **API error responses**: every error is an RFC 9457 Problem Detail (`backend/src/main/java/com/srm/creditengine/shared/api/ApiExceptionHandler.java`) with a stable `code` field (e.g. `INVALID_CREDENTIALS`, `LOGIN_RATE_LIMITED`, `IDEMPOTENCY_KEY_REUSED`, `ALREADY_SETTLED`, `ALREADY_REVERSED`, `VALIDATION_FAILED`, `ACCESS_DENIED`, `AUTHENTICATION_REQUIRED`, `INTERNAL_ERROR`) and a `correlationId` sourced from `CorrelationIdFilter`. Use the `code` to distinguish causes; use `correlationId` to find the request in logs.
- **`403` / `ACCESS_DENIED`**: the authenticated role lacks the matcher for that endpoint. Check [`PERMISSION_MATRIX.md`](PERMISSION_MATRIX.md) against the actual `SecurityConfiguration.java` matchers before assuming a bug.
- **`401` / `AUTHENTICATION_REQUIRED`**: missing or invalid JWT `Authorization: Bearer` header, or the token is expired (tokens are short-lived, per `docs/adr/0005-local-jwt-and-oidc-evolution.md`).
- **`429` / `LOGIN_RATE_LIMITED`**: repeated failed logins against `/api/v1/auth/login` tripped the local rate limiter; wait and retry with correct credentials.
- **`test-log-redaction` failure**: a log line leaked a credential, JWT, idempotency key, or unrestricted financial payload. Fix the leaking log statement — do not weaken the test.
- **Secrets scan / pre-commit hook rejects a commit**: a fixture or file matched a secret pattern. A fake-secret fixture must be unmistakably non-live and explicitly allowlisted by exact path/reason; do not disable the hook.
- **`make test-api-features` failure**: inspect `backend/build/reports/cucumber.json` or `.html` and the matching stable scenario ID in `backend/src/integrationTest/resources/features/srm_acceptance.feature`.
- **`make test-ui-features` failure**: inspect `frontend/playwright-report/` and `frontend/test-results/`; Playwright startup or cleanup failures are failures, not skipped evidence.
- **Representative EXPLAIN failure**: inspect the command output before the artifact. The gate fails if the real backend does not apply every repository Flyway migration, a current reporting index is absent, the shared SQL-template markers drift, or any assignor/asset-currency/settlement-currency/product-type case is empty or non-selective. Inspect `docs/evidence/reporting-explain.txt` only after a successful current run. PostgreSQL may legitimately choose sequential scans, and this remains query-shape—not production-capacity—proof.
- **`make security-scan` failure**: generated reports are under `build/security/`. Every secret finding and every HIGH/CRITICAL vulnerability with a published fix fails the gate. Any accepted unfixed CVE requires a specific, owned, expiring `.trivyignore.yaml` entry; blanket `ignore-unfixed` policy is forbidden.
- **Documentation or traceability failure**: run `make validate-docs` and `make validate-traceability` separately. Fix the source, path, scenario mapping, architecture text, or schema inventory; do not weaken the validator.
- **`make release-check` failure**: the aggregate stops on the first failed or blocked sub-gate. A local aggregate does not execute or authorize CodeQL, a remote push, pull request, tag, or release.

## 5. Seeding and resetting fixtures

Fixture loading runs as one-shot Spring profiles inside the `backend` image, invoked through Compose's `fixtures` profile (`compose.yaml`):

```bash
docker compose --profile fixtures run --rm dev-fixtures  # ad hoc local/dev reference data
docker compose --profile fixtures run --rm e2e-fixtures  # fixed-instant (2030-01-15T12:00:00Z)
                                                           # deterministic fixtures for E2E
```

Both are idempotent: `scripts/verify-e2e-fixtures.sh` runs `e2e-fixtures` twice and asserts the resulting `runtime_fixture_records` and pinned exchange-rate row (`id = 00000000-0000-0000-0000-000000000202`) produce identical checksums, and that each fixed fixture ID (`e2e-clock`, `e2e-usd-brl-rate`, `e2e-assignor-id`) occurs exactly once. `dev-fixtures` contains no expiry-sensitive data.

**There is no reset endpoint and no production fixture profile.** To reset state, stop the stack and drop the named volume:

```bash
docker compose down -v --remove-orphans   # -v removes the postgres-data volume
docker compose up --build --wait
```

To reseed local reviewer accounts outside fixtures, restart `backend` with the `dev` profile active and either or both `SRM_DEV_OPERATOR_*` and `SRM_DEV_ADMIN_*` pairs set. `DevelopmentOperatorSeeder` uses `insert ... on conflict (email) do nothing`, so repeating the seed is safe and independently preserves the intended roles.

## 6. Local Git hooks

```bash
make install-hooks
```

Installs the pre-push hook that runs the same backend/frontend unit suite as `make verify-unit`, plus the commit-message and pre-commit secret hooks validated by `make test-hooks`.
