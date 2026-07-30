# Human and tooling usage record

## Purpose and scope

This document separates human authority and deterministic engineering tools from the AI assistance recorded in [`AI_USAGE.md`](AI_USAGE.md). It describes controls and executable evidence; it does not claim that a person manually performed a check unless a command result or external review record establishes that fact.

## Human controls

- The repository owner retains responsibility for requirements, design acceptance, credentials, and the delivered code.
- A local command, report, or AI review cannot authorize remote repository creation, visibility changes, pushes, pull requests, merges, publication, tags, or releases.
- SDD 12 requires explicit owner/account/repository authorization before those actions. No such remote, tag, or release action is claimed here.
- Implementation and approval are separate concerns. Focused code, security, documentation, and verifier lanes were used; deterministic evidence remains the acceptance authority.
- Production-readiness and capacity claims require production-like infrastructure and measured evidence. The 10,000-row PostgreSQL plan is representative query evidence only.

## Toolchain and purpose

| Tool | Use in this repository |
| --- | --- |
| Java 21, Gradle, JUnit, AssertJ, Mockito | Backend compilation, unit/API contracts, deterministic financial boundaries |
| PostgreSQL 16, Flyway, Testcontainers, Cucumber | Authoritative schema, immutable-history guards, transactional concurrency/rollback, executable acceptance scenarios |
| Node/npm, TypeScript, Vite, Vitest, React Testing Library, jest-axe | Frontend build, state/component behavior, accessibility |
| Playwright/Chromium | Real-browser E2E-001 through the frontend, backend, and PostgreSQL |
| Docker Compose | Reviewer runtime, deterministic fixtures, service health, readiness loss/recovery, edge/internal network topology |
| PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` | Representative statement-query plan and row-count evidence |
| Gitleaks, Trivy, Syft/CycloneDX, dependency-license checkers | Full-history/content secret detection, filesystem/runtime-image CVEs and misconfiguration, immutable image digests, SBOM, production-license allowlists |
| GitHub Actions and CodeQL | Pinned CI orchestration, least-privilege permissions, and remote static analysis when the workflow runs |
| Repository validators | README architecture, migration-to-schema coverage, links, OpenAPI reachability, traceability, CI-contract, and prohibited-claim checks |
| Git hooks | Conventional commit-message policy; fast staged canary/private-key/credential-assignment guard with explicit placeholder rules; pre-push unit verification. Full-history/content Gitleaks and Trivy remain the authoritative secret gates |

## Evidence map

| Evidence | Command | Path or result |
| --- | --- | --- |
| Backend scenarios | `make test-api-features` | `backend/build/reports/cucumber.json` and `.html` |
| PostgreSQL integrations | `make test-runtime` | `backend/build/reports/tests/integrationTest/` |
| Browser critical path | `make test-ui-features` | `frontend/playwright-report/`, `frontend/test-results/` |
| Full reviewer runtime | `make verify-compose` | Full-stack smoke, authenticated metrics, fixtures, readiness recovery; stack removed on exit |
| Reporting plan | `make explain-statements-representative` | `docs/evidence/reporting-explain.txt` |
| Security/SBOM/licenses | `make security-scan` | Generated `build/security/` reports, image digests, and `sbom.cdx.json` |
| Documentation | `make validate-docs` | Link, schema, OpenAPI, and claim-validation results |
| Scenario mapping | `make validate-traceability` | Exact SDD ID → source/command validation |
| Aggregate local gate | `make release-check` | All local build, runtime, acceptance, security, and documentation evidence above |

Generated artifacts can be stale. A reviewer should pair each artifact with the current command result and inspect failures rather than accepting file presence.

## Financial and security controls

- Java `BigDecimal`, PostgreSQL `NUMERIC`, and `HALF_EVEN` currency boundaries protect authoritative money calculations; JavaScript only sends and renders decimal strings.
- Pricing Quote snapshots retain the inputs, rate/spread/strategy, term, FX observation, and output used. PostgreSQL guards permit only the lifecycle transition `ACTIVE` → `CONSUMED` without snapshot mutation.
- Exchange-rate, Settlement, Settlement Item, Reversal, and audit history is append-only in PostgreSQL. The ledger is a derived SQL read model with deterministic UUID identity and signed compensating rows.
- Settlement creation is atomic and idempotent, uses optimistic state checks plus uniqueness constraints, and is never automatically retried.
- Metrics accept only bounded domain labels; credentials, tokens, idempotency keys, and business identifiers are excluded. Prometheus access is ADMIN-only while orchestration health probes remain public.
- Security gates fail on every secret finding and every HIGH/CRITICAL vulnerability with a published fix. Any accepted unfixed CVE requires a specific, justified, owned, expiring `.trivyignore.yaml` entry.

## Limitations

Implemented evidence is local or CI-oriented exercise evidence. It does not establish a real OIDC provider, real market-data integration, refresh-token lifecycle, FX triangulation, Kubernetes/Terraform, microservices, production throughput, one million transactions per minute, or an authorized remote publication/release. A historical local annotated `v1.0.0` exists at `af898ef`; it is not evidence for the current HEAD and was not moved or reused. Hosted PR/check/review state and any future version tag remain Gap items in [`docs/REQUIREMENT_TRACEABILITY.md`](docs/REQUIREMENT_TRACEABILITY.md).
