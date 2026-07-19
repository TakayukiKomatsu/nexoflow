# AI usage record

## Scope and responsibility

AI agents were used as engineering copilots for repository exploration, requirements/SDD comparison, implementation, debugging, test design, security and code review, verification, and documentation reconciliation. Work was split into focused planning, execution, review, and verifier lanes; agent records are retained under `docs/.pi-subagents/artifacts/`.

AI output was never treated as evidence by itself. Deterministic builds, tests, runtime probes, database assertions, browser execution, scanners, and document validators establish the observed result. The human owner retains responsibility for code review, design decisions, credentials, and every authorization-gated remote, publication, tag, or release action.

## Strategic prompts and outcomes

- “Read the docs available on docs and check our SDD plan and implementation.” Produced the original gap review against `docs/README_case_dev_srm.md` and the 12-prompt SDD suite.
- “Sure, lets fix everything, and check the code base as well.” Drove focused implementation and independent review lanes across backend, frontend, runtime, acceptance, security, and reviewer documentation.
- The final reconciliation corrected claims only after executable evidence existed; proposed scale, external identity/FX integration, and remote release actions remain explicitly unimplemented.

## Material corrections during review

- Replaced an executability-only hook test with checks that the referenced Make target exists and works.
- Replaced a manually assembled, non-verifying JWT path with Spring Security JWT encoding/decoding, BCrypt credentials, and explicit closed-enum role conversion.
- Removed runtime H2/default-secret ambiguity: PostgreSQL/environment configuration is required outside isolated tests.
- Strengthened exact strategy vectors, money/rate boundaries, and immutable Pricing Quote round trips.
- Added PostgreSQL-backed deterministic settlement concurrency and fault-injection rollback evidence.
- Added PostgreSQL guards for immutable quote snapshots, FX history, Settlement/Reversal history, and audit records.
- Corrected signed ledger identity to stable deterministic UUIDs with positive Settlement and negative Reversal entries.
- Corrected the authorization matrix, stale/cancellation browser state, Settlement idempotency-key lifecycle, and Compose reviewer credential separation.
- Replaced string-presence checks with executable Cucumber, Playwright, PostgreSQL EXPLAIN, Mermaid/schema/OpenAPI, security, SBOM, license, CI-contract, and traceability gates.

## Evidence and limitations

| Area | Deterministic evidence |
| --- | --- |
| Backend acceptance | `make test-api-features`; `backend/build/reports/cucumber.json` |
| Browser critical path | `make test-ui-features`; `frontend/playwright-report/` |
| PostgreSQL integrity | `make test-runtime`; `backend/build/reports/tests/integrationTest/` |
| Full-stack runtime | `make verify-compose` |
| Representative reporting plan | `make explain-statements-representative`; `docs/evidence/reporting-explain.txt` |
| Security and dependencies | `make security-scan`; generated `build/security/` reports and SBOM |
| Documentation and mappings | `make validate-docs`; `make validate-traceability` |
| Aggregate local evidence | `make release-check` |

AI-assisted work covers Testcontainers PostgreSQL, Compose, deterministic mock FX, Settlement/reporting, Cucumber, Playwright E2E-001, representative query-plan evidence, and local security/release gates. It does **not** provide a real market-data provider, external OIDC, production-scale/1M-transactions-per-minute proof, Kubernetes/Terraform/microservices, or authorization for a push, pull request, tag, publication, or release.
