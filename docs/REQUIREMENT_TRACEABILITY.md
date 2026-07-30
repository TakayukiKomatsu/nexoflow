# Requirement traceability

Source: [`README_case_dev_srm.md`](./README_case_dev_srm.md). Status is restricted to **Implemented**, **Proposed**, **Pending hosted evidence**, or **Gap**. An Implemented row requires an executable current command and a concrete source/report path; a generated artifact is not a pass without its current command result.

## Source requirement matrix

| Source requirement | Status | Implementation and evidence | Verification |
| --- | --- | --- | --- |
| Java backend and modern SPA | **Implemented** | Modular Spring API and React operator workflow; `backend/src/main/java/com/srm/creditengine/` and `frontend/src/App.tsx` | `make build` |
| Currency rates and mocked integration | **Implemented** | Effective-at direct/inverse/identity conversion, 24-hour freshness, append-only observations, bounded retry/circuit behavior, deterministic HTTP mock; `backend/src/main/java/com/srm/creditengine/currency/` | `make test-api-features` |
| Strategy pricing and cross-currency conversion | **Implemented** | Product strategies call shared exact decimal math; immutable complete 15-minute quote snapshots and `ACTIVE` → `CONSUMED` lifecycle; `backend/src/main/java/com/srm/creditengine/pricing/` | `make test-api-features` |
| Relational ACID Settlement and race protection | **Implemented** | Atomic preview/create, idempotent replay/conflict, real-PostgreSQL barrier concurrency, injected-failure rollback; `backend/src/integrationTest/java/com/srm/creditengine/settlement/` | `make test-runtime` |
| REST/OpenAPI and controlled errors | **Implemented** | `/api/v1` controllers, RFC 9457 codes/correlation, generated OpenAPI and health contracts; `backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java` | `make validate-docs` |
| Filtered optimized settlement statement | **Implemented** | Authorized filters, bounded pagination, stable derived UUIDs, signed Settlement/Reversal entries, and representative PostgreSQL evidence rendered from the production SQL template over the real Flyway schema; `backend/src/main/resources/sql/settlement-statement.sql`, `docs/evidence/reporting-explain.txt` | `make test-reporting-evidence-contract && make explain-statements-representative` |
| Operator simulation and paginated ledger | **Implemented** | Auth, server-authoritative stale/cancel-safe simulation, quote/preview/intent retry, URL-backed ledger, accessibility and browser critical path; `frontend/src/App.test.tsx`, `frontend/src/a11y.test.tsx`, `frontend/e2e/operator-critical-path.spec.ts` | `make test-ui-features` |
| Docker/Compose and operational quality | **Implemented** | Internal PostgreSQL/backend/mock-FX network, edge-only frontend, deterministic fixtures, dependency-aware readiness, authenticated bounded metrics; `compose.yaml` | `make verify-compose` |
| Hooks, CI, security, SBOM, licenses, docs | **Implemented** | Immutable CI actions, CodeQL job, Gitleaks/Trivy/Syft/license gates, Mermaid/schema/OpenAPI/link/claim validation; `.github/workflows/ci.yml`, `scripts/security-scan.sh` | `make security-scan && make validate-docs && make validate-traceability` |
| ADRs, ER, runbook, incident procedure, usage disclosure | **Implemented** | Full decision records, SQL/Java migration inventory mirrored by ER, state/sequence diagrams, runbook, permission matrix, incident rollback procedure, and AI/tooling records; `docs/architecture/schema-inventory.md`, `docs/architecture/settlement-sequence.mmd`, `docs/RUNBOOK.md`, `AI_USAGE.md`, `HT_USAGE.md` | `make validate-docs` |
| Evolution to 1M transactions/minute, EDA, partitioning | **Proposed** | Quantitative design proposal in `README.md` and `docs/architecture/scale-evolution.mmd`; explicitly not implemented and no production throughput/capacity proof | `make validate-docs` |
| External OIDC, real market FX, Kubernetes/Terraform, microservices | **Gap** | Deliberately outside the exercise; local JWT/BCrypt and deterministic mock FX remain the implemented adapters | `make validate-docs` |
| Remote collaboration, publication, tag, release | **Pending hosted evidence** | The public [Nexoflow repository](https://github.com/TakayukiKomatsu/nexoflow) and hosted [cleanup pull request #1](https://github.com/TakayukiKomatsu/nexoflow/pull/1) exist. Initial hosted CI [run `30502414061`](https://github.com/TakayukiKomatsu/nexoflow/actions/runs/30502414061) failed because Verify encountered missing Git identity in a disposable collaboration simulation while License compliance and security scan separately lacked the Java wrapper's former `zsh` dependency. The wrapper is portable and disposable Git-history simulations have been removed from CI, but no hosted green run, reviewer approval, merge, new release tag, or public release has been observed; `v1.0.0` at `af898ef` remains historical | Hosted links record publication, the open PR, and observed CI outcomes |

## Stable SDD scenario mapping

| Scenario ID | Status | Executable source | Command and observed artifact |
| --- | --- | --- | --- |
| FIN-GIT-001 | **Implemented** | `scripts/tests/test_commit_message_hook.sh` | `make test-hooks`; disposable hook-test output |
| FIN-GIT-002 | **Implemented** | `scripts/tests/test_pre_commit_secret_hook.sh` | `make test-hooks`; disposable secret-canary output |
| OPS-RUN-001 | **Implemented** | `scripts/verify-readiness-recovery.sh` | `make verify-compose`; readiness loss/recovery output |
| OPS-FIX-002 | **Implemented** | `scripts/verify-e2e-fixtures.sh` | `make fixtures-e2e`; deterministic checksum output |
| AUTH-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| FX-004 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| FX-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| FX-RES-006 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| PRICE-001 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| PRICE-002 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| QUOTE-005 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| SETTLE-006 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| SETTLE-ROLLBACK-008 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| REPORT-REV-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| REVERSE-007 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| UI-SIM-002 | **Implemented** | `frontend/src/App.test.tsx`; exact browser proof: `frontend/e2e/operator-critical-path.spec.ts` changes the live invoice simulation to `POST_DATED_CHEQUE`, observes exactly one pricing-simulation POST, and renders the selected cheque product with returned amount `966.18` | `make test-unit`; `make test-ui-features` |
| UI-SIM-005 | **Implemented** | deterministic stale-response proof: `frontend/src/App.test.tsx`; `frontend/e2e/operator-critical-path.spec.ts` is generic live-simulation browser smoke only | `make test-unit`; `make test-ui-features` |
| UI-SETTLE-004 | **Implemented** | `frontend/src/SettlementWorkspace.test.tsx`; `frontend/e2e/operator-critical-path.spec.ts` performs browser preview and confirm, then its `APIRequestContext` repeats the confirmed request with the same idempotency key and asserts replay | `make test-unit`; `make test-ui-features` |
| UI-LEDGER-006 | **Implemented** | `frontend/src/SettlementWorkspace.test.tsx`; `frontend/e2e/operator-critical-path.spec.ts` uses the browser's URL-backed currency filter, `page=0` query, and back/forward restoration, then reads ledger rows after an API reversal | `make test-unit`; `make test-ui-features` |
| OBS-003 | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-api-features`; `backend/build/reports/cucumber.json` |
| E2E-001 | **Implemented** | `frontend/e2e/operator-critical-path.spec.ts` | `make test-ui-features`; `frontend/playwright-report/index.html` |
| REL-004 | **Pending hosted evidence** | `docs/sdd/12_sdd_authorized-collaboration-crisis-publication-and-release-evidence.md`; publication exists at [`3a01ab7`](https://github.com/TakayukiKomatsu/nexoflow/commit/3a01ab7c97aa9730fb29f4d58e3b84fb8a478cfe) and [cleanup pull request #1](https://github.com/TakayukiKomatsu/nexoflow/pull/1) is open, while hosted green CI, reviewer approval, merge, and new release evidence remain absent | `make validate-docs` validates local release-truth claims; hosted checks remain authoritative |
| CRISIS-002 | **Gap** | The CI-brittle disposable Git-history simulation was removed; operational rollback remains documented in `docs/sdd/12_sdd_authorized-collaboration-crisis-publication-and-release-evidence.md` and `docs/RUNBOOK.md` | `make validate-docs`; no local Git mutation simulation is claimed |

## Supplemental documentation and contract checks

| Check ID | Status | Executable source | Command and observed artifact |
| --- | --- | --- | --- |
| DOC-LINK-001 | **Implemented** | `docs/sdd/11_sdd_staff-artifacts-and-reviewer-documentation.md`; `scripts/tests/test_architecture_docs.sh` | `make validate-docs`; rendered local-link and Mermaid checks |
| DOC-SCHEMA-002 | **Implemented** | `docs/sdd/11_sdd_staff-artifacts-and-reviewer-documentation.md`; `scripts/tests/test_architecture_docs.sh` | `make validate-docs`; migration-to-ER and OpenAPI checks |
| DOC-TRACE-003 | **Implemented** | `scripts/validate-traceability.sh` | `make validate-traceability`; traceability path and command resolver |
| DOC-MONEY-004 | **Implemented** | `docs/sdd/11_sdd_staff-artifacts-and-reviewer-documentation.md`; `backend/src/test/java/com/srm/creditengine/pricing/application/PricingExactVectorTest.java` | `make validate-docs && make test-unit`; documentation gate plus exact decimal `975.61` vector assertions |
| DOC-CLAIM-005 | **Implemented** | `docs/sdd/11_sdd_staff-artifacts-and-reviewer-documentation.md`; `scripts/validate-docs.sh` | `make validate-docs`; forbidden-claim scan |
| AUTHORITY-001 | **Implemented** | `frontend/scripts/validate-authoritative-pricing.mjs` | `make validate-frontend-authority`; browser-side financial-arithmetic guard |
| API-CONTRACT-001 | **Implemented** | `frontend/scripts/validate-pricing-quote-contract.mjs` | `make validate-frontend-api-contract`; frontend quote/OpenAPI boundary guard |
| DOC-OPENAPI-006 | **Implemented** | `scripts/validate-api-docs.mjs`; `docs/architecture/api-endpoints.md` | `make validate-docs`; freshly exported canonical OpenAPI-operation-to-inventory comparison plus executable contract |
| FIN-GIT-003 | **Implemented** | `scripts/tests/test_pre_commit_secret_hook.sh` | `make test-hooks`; generic credential assignment rejection and explicit-placeholder acceptance |
| Historical audit reconciliation | **Implemented** | `docs/evidence/historical/2026-07-22-audit-discrepancies.md`; resolved findings are retained as historical evidence and guarded from returning as active claims | `make validate-architecture-docs` |

## Supplementary SDD 04–10 proof matrix

| SDD scope | Status | Exact executable source | Command and evidence |
| --- | --- | --- | --- |
| SDD 04 pricing vectors and immutable quotes | **Implemented** | `backend/src/test/java/com/srm/creditengine/pricing/application/PricingExactVectorTest.java`; `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-unit && make test-api-features`; exact decimal, cross-currency, and snapshot scenarios |
| SDD 05 settlement conflict, replay, stale/duplicate, and rollback | **Implemented** | `backend/src/integrationTest/resources/features/srm_acceptance.feature`; `backend/src/integrationTest/java/com/srm/creditengine/settlement/application/JdbcSettlementServiceConcurrencyIntegrationTest.java`; `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementAtomicityIntegrationTest.java` | `make test-api-features && make test-runtime`; Cucumber and real-PostgreSQL concurrency/rollback evidence |
| SDD 06 ledger reporting boundaries, filters, roles, pages, and no-N+1 behavior | **Implemented** | `backend/src/main/resources/sql/settlement-statement.sql`; `backend/src/test/java/com/srm/creditengine/reporting/application/JdbcSettlementStatementServiceTest.java`; `backend/src/integrationTest/java/com/srm/creditengine/reporting/SettlementStatementPostgresIntegrationTest.java`; `scripts/tests/test_reporting_evidence_contract.sh`; `backend/src/integrationTest/resources/features/srm_acceptance.feature` | `make test-unit && make test-runtime && make test-api-features && make test-reporting-evidence-contract`; bounded filters, authorized statements, stable pages, counted single-query movements, and one production/evidence SQL authority |
| SDD 07 frontend authentication and live simulation | **Implemented** | `frontend/src/App.test.tsx`; `frontend/src/a11y.test.tsx`; `frontend/e2e/operator-critical-path.spec.ts` | `make test-unit && make test-ui-features`; component/accessibility proof plus E2E-001 browser smoke |
| SDD 08 preview, settlement intent, and reversal ledger | **Implemented** | `frontend/src/SettlementWorkspace.test.tsx`; `frontend/e2e/operator-critical-path.spec.ts` | `make test-unit && make test-ui-features`; retry-safe intent, signed ledger, and E2E-001 browser smoke |
| SDD 09 observability redaction and metric cardinality | **Implemented** | `backend/src/test/java/com/srm/creditengine/shared/runtime/FinancialTelemetryTest.java`; `backend/src/integrationTest/resources/features/srm_acceptance.feature`; `scripts/tests/test_log_redaction.sh` | `make test-unit && make test-api-features && make test-log-redaction`; bounded labels and redaction evidence |
| SDD 10 deterministic fixtures, security gates, browser evidence, and reporting query shape | **Implemented** | `scripts/verify-e2e-fixtures.sh`; `scripts/security-scan.sh`; `frontend/e2e/operator-critical-path.spec.ts`; `scripts/explain-statements-representative.sh`; `scripts/render-statement-evidence-sql.mjs`; `docs/evidence/reporting-explain.txt` | `make fixtures-e2e && make security-scan && make test-ui-features && make test-reporting-evidence-contract && make explain-statements-representative`; deterministic checksum, scan, browser, real-migration schema, shared-query filter matrix, and representative query-shape evidence (not guaranteed index selection) |

## Reviewer qualifications

- `docs/evidence/reporting-explain.txt` is a PostgreSQL 16 filter-matrix plan over 10,000 representative Settlement rows on the real migrated schema, not production-scale or 1M-transactions/minute proof.
- The runtime service and evidence renderer consume `backend/src/main/resources/sql/settlement-statement.sql`; the evidence gate verifies every current reporting index and positive selective counts for assignor, asset currency, settlement currency, product type, and their combined case.
- The representative plans may choose sequential scans when PostgreSQL estimates them cheaper; they demonstrate the native-SQL read model at 10,000 rows, not guaranteed index scans.
- CodeQL runs in GitHub Actions; local `make security-scan` covers Gitleaks, Trivy, immutable image digests, CycloneDX SBOM, and production licenses.
- Generated reports under `backend/build/`, `frontend/`, and `build/security/` must be paired with the current command result.
- Local signed JWT/BCrypt, deterministic mock FX, and authorization-gated remote release are retained limitations, not inferred production capabilities.
