# Requirement traceability

Source: [`README_case_dev_srm.md`](./README_case_dev_srm.md). Status is restricted to **Implemented**, **Proposed**, or **Gap**. An Implemented row requires an executable current command and a concrete source/report path; a generated artifact is not a pass without its current command result.

## Source requirement matrix

| Source requirement | Status | Implementation and evidence | Verification |
| --- | --- | --- | --- |
| Java backend and modern SPA | **Implemented** | Modular Spring API and React operator workflow; `backend/src/main/java/com/srm/creditengine/` and `frontend/src/App.tsx` | `make build` |
| Currency rates and mocked integration | **Implemented** | Effective-at direct/inverse/identity conversion, 24-hour freshness, append-only observations, bounded retry/circuit behavior, deterministic HTTP mock; `backend/src/main/java/com/srm/creditengine/currency/` | `make test-api-features` |
| Strategy pricing and cross-currency conversion | **Implemented** | Product strategies call shared exact decimal math; immutable complete 15-minute quote snapshots and `ACTIVE` → `CONSUMED` lifecycle; `backend/src/main/java/com/srm/creditengine/pricing/` | `make test-api-features` |
| Relational ACID Settlement and race protection | **Implemented** | Atomic preview/create, idempotent replay/conflict, real-PostgreSQL barrier concurrency, injected-failure rollback; `backend/src/integrationTest/java/com/srm/creditengine/settlement/` | `make test-runtime` |
| REST/OpenAPI and controlled errors | **Implemented** | `/api/v1` controllers, RFC 9457 codes/correlation, generated OpenAPI and health contracts; `backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java` | `make validate-docs` |
| Filtered optimized settlement statement | **Implemented** | Authorized filters, bounded pagination, stable derived UUIDs, signed Settlement/Reversal entries, representative PostgreSQL index plan; `docs/evidence/reporting-explain.txt` | `make explain-statements-representative` |
| Operator simulation and paginated ledger | **Implemented** | Auth, server-authoritative stale/cancel-safe simulation, quote/preview/intent retry, URL-backed ledger, accessibility and browser critical path; `frontend/src/App.test.tsx`, `frontend/src/a11y.test.tsx`, `frontend/e2e/operator-critical-path.spec.ts` | `make test-ui-features` |
| Docker/Compose and operational quality | **Implemented** | Internal PostgreSQL/backend/mock-FX network, edge-only frontend, deterministic fixtures, dependency-aware readiness, authenticated bounded metrics; `compose.yaml` | `make verify-compose` |
| Hooks, CI, security, SBOM, licenses, docs | **Implemented** | Immutable CI actions, CodeQL job, Gitleaks/Trivy/Syft/license gates, Mermaid/schema/OpenAPI/link/claim validation; `.github/workflows/ci.yml`, `scripts/security-scan.sh` | `make security-scan && make validate-docs && make validate-traceability` |
| ADRs, ER, runbook, crisis proof, usage disclosure | **Implemented** | ADRs, migrations V1–V16 mirrored by ER, runbook, permission matrix, AI/tooling records, disposable regression/revert proof; `docs/architecture/er-diagram.mmd`, `docs/RUNBOOK.md`, `AI_USAGE.md`, `HT_USAGE.md` | `make test-crisis-evidence` |
| Evolution to 1M transactions/minute, EDA, partitioning | **Proposed** | Design analysis only in this plan and architecture artifacts; no production throughput/capacity proof | `make validate-docs` |
| External OIDC, real market FX, Kubernetes/Terraform, microservices | **Gap** | Deliberately outside the exercise; local JWT/BCrypt and deterministic mock FX remain the implemented adapters | `make validate-docs` |
| Remote collaboration, publication, tag, release | **Gap** | Blocked pending explicit human authorization under SDD 12; no remote mutation is claimed | `make test-crisis-evidence` |

## Stable SDD scenario mapping

| Scenario ID | Status | Executable source | Command and observed artifact |
| --- | --- | --- | --- |
| FIN-GIT-001 | **Implemented** | `scripts/tests/test_commit_message_hook.sh` | `make test-hooks`; disposable hook-test output |
| FIN-GIT-002 | **Implemented** | `scripts/tests/test_pre_commit_secret_hook.sh` | `make test-hooks`; disposable secret-canary output |
| OPS-RUN-001 | **Implemented** | `scripts/verify-readiness-recovery.sh` | `make verify-compose`; readiness loss/recovery output |
| OPS-FIX-002 | **Implemented** | `scripts/verify-e2e-fixtures.sh` | `make fixtures-e2e`; deterministic checksum output |
| AUTH-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| FX-004 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| FX-RES-006 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| PRICE-001 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| QUOTE-005 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| SETTLE-006 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| SETTLE-ROLLBACK-008 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| REPORT-REV-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| REVERSE-007 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| UI-SIM-002 | **Implemented** | `frontend/src/App.test.tsx` | `npm --prefix frontend test -- --run`; frontend Vitest report |
| UI-SIM-005 | **Implemented** | `frontend/src/App.test.tsx` | `npm --prefix frontend test -- --run`; frontend Vitest report |
| UI-SETTLE-004 | **Implemented** | `frontend/src/SettlementWorkspace.test.tsx` | `npm --prefix frontend test -- --run`; frontend Vitest report |
| UI-LEDGER-006 | **Implemented** | `frontend/src/SettlementWorkspace.test.tsx` | `npm --prefix frontend test -- --run`; frontend Vitest report |
| OBS-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| E2E-001 | **Implemented** | `frontend/e2e/operator-critical-path.spec.ts` | `make test-ui-features`; `frontend/playwright-report/index.html` |
| REL-004 | **Gap** | `docs/sdd/12_sdd_authorized-collaboration-crisis-publication-and-release-evidence.md` | `make test-crisis-evidence`; local evidence only, publication remains blocked |
| CRISIS-002 | **Implemented** | `scripts/test-crisis-evidence.sh` | `make test-crisis-evidence`; disposable branch/hash/recovery output |

## Reviewer qualifications

- `docs/evidence/reporting-explain.txt` is a PostgreSQL 16 plan over 10,000 representative Settlement rows, not production-scale or 1M-transactions/minute proof.
- CodeQL runs in GitHub Actions; local `make security-scan` covers Gitleaks, Trivy, immutable image digests, CycloneDX SBOM, and production licenses.
- Generated reports under `backend/build/`, `frontend/`, and `build/security/` must be paired with the current command result.
- Local signed JWT/BCrypt, deterministic mock FX, and authorization-gated remote release are retained limitations, not inferred production capabilities.
