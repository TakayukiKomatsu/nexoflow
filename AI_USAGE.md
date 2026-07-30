# AI usage record

## Scope and responsibility

AI agents were used as engineering copilots for repository exploration, requirements/SDD comparison, implementation, debugging, test design, security and code review, verification, and documentation reconciliation. Work was split into focused planning, execution, review, and verifier lanes. Raw execution records, when produced, remain local under the ignored `.pi-subagents/` workspace because they can contain transient machine context; they are not claimed as committed reviewer evidence. Sanitized decisions, plans, corrections, and command-backed outcomes are retained in `docs/superpowers/`, `docs/evidence/`, this disclosure, and the Git history.

AI output was never treated as evidence by itself. Deterministic builds, tests, runtime probes, database assertions, browser execution, scanners, and document validators establish the observed result. The human owner retains responsibility for code review, design decisions, credentials, and every authorization-gated remote, publication, tag, or release action.

## Strategic prompts and outcomes

- “Read the docs available on docs and check our SDD plan and implementation.” Produced the original gap review against `docs/README_case_dev_srm.md` and the 12-prompt SDD suite.
- “Sure, lets fix everything, and check the code base as well.” Drove focused implementation and independent review lanes across backend, frontend, runtime, acceptance, security, and reviewer documentation.
- “Validate the frontend and the backend, focusing on senior/staff level.” Split independent backend, frontend, financial-integrity, and delivery/governance reviews, then reconciled their command evidence rather than averaging conclusions.
- “Fix everything” was decomposed into bounded remediations with explicit file ownership, regression seams, focused Conventional Commits, and re-execution of the relevant gates. Remote publication, tag mutation, and hosted review were kept outside the authorization boundary.
- The final reconciliation corrected claims only after executable evidence existed; proposed scale, external identity/FX integration, and remote release actions remain explicitly unimplemented.

The prompts were useful for breadth and parallelism, but they were not accepted as
specifications by themselves. The case brief, canonical SDD, migrations, OpenAPI,
tests, and observed command results remained the sources of truth.

## Hallucinations and rework caught by verification

- An early review accepted `make security-scan` as green. A macOS Bash 3.2
  empty-array expansion could stop before Gitleaks while the cleanup trap returned
  success. A disposable fake-scanner regression now proves premature failures are
  non-zero and that every report is produced before a phase can pass.
- Documentation said there were ten Cucumber scenarios; the executable feature
  contains twelve. The validator now counts the feature rather than trusting prose.
- Documentation said no tag had been created. `git show v1.0.0` proved an existing
  local annotated tag at `af898ef`; current docs preserve it as historical and do
  not misrepresent it as a reviewed or remote release.
- The original Markdown test captured only the last link on a line and excluded
  the root README. The corrected validator extracts every link occurrence across
  reviewer Markdown.
- String-presence checks overstated frontend/OpenAPI and migration/ER agreement.
  Exact endpoint inventory and SQL/Java-migration column/constraint validators now
  fail on undocumented drift.

These are examples of AI-assisted work creating both useful hypotheses and
plausible-but-wrong conclusions. Deterministic counterexamples, not confidence or
agent consensus, decided the correction.

## Estimated time saved and lost

These ranges are a retrospective human estimate, not instrumented telemetry.

| Effect | Estimated time | Basis |
| --- | ---: | --- |
| Saved | 12–18 hours | Parallel repository mapping, test-gap enumeration, first-pass fixture/validator scaffolding, and cross-document consistency searches |
| Lost or reinvested | 5–8 hours | Reproducing false passes, rejecting shallow/string-only checks, correcting stale claims, and reviewing generated patches for financial/security accuracy |
| Estimated net | 7–10 hours | Gross saved time minus verification and rework; excludes normal implementation/test runtime that would exist without AI |

The cost column is material: without independent tests, the apparent saving would
have hidden defects rather than accelerated delivery.

## Material corrections during review

- Replaced an executability-only hook test with checks that the referenced Make target exists and works.
- Replaced a manually assembled, non-verifying JWT path with Spring Security JWT encoding/decoding, BCrypt credentials, and explicit closed-enum role conversion.
- Removed runtime H2/default-secret ambiguity: PostgreSQL/environment configuration is required outside isolated tests.
- Strengthened exact strategy vectors, money/rate boundaries, and immutable Pricing Quote round trips.
- Added PostgreSQL-backed deterministic settlement concurrency and fault-injection rollback evidence.
- Added PostgreSQL guards for immutable quote snapshots, FX history, Settlement/Reversal history, and audit records.
- Corrected signed ledger identity to stable deterministic UUIDs with positive Settlement and negative Reversal entries.
- Corrected the authorization matrix, stale/cancellation browser state, Settlement idempotency-key lifecycle, and Compose reviewer credential separation.
- Replaced string-presence checks with executable Cucumber, Playwright, PostgreSQL EXPLAIN, schema/OpenAPI, security, SBOM, license, CI-contract, and traceability gates.

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
