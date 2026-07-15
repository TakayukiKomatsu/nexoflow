# SRM Credit Engine — 12-Prompt SDD Suite

Execute from the **repository root** in numeric order. Each prompt is self-contained: it embeds the mandatory global execution contract, prerequisites, scope/non-goals, contracts, executable acceptance, test mapping, focused/regression commands, evidence, and commit outcomes.

The canonical combined suite is [`docs/SDD_PROMPTS.md`](../SDD_PROMPTS.md). Source requirements are [`docs/README_case_dev_srm.md`](../README_case_dev_srm.md) and [`docs/SRM_REQUIREMENTS_PLAN.md`](../SRM_REQUIREMENTS_PLAN.md).

| Day | # | Increment | Prompt |
|---:|---:|---|---|
| 1 | 01 | Governed repository and architecture foundation | [01](./01_sdd_governed-repository-and-architecture-foundation.md) |
| 1 | 02 | Full-stack runtime, API conventions, and deterministic fixtures | [02](./02_sdd_full-stack-runtime-api-conventions-and-deterministic-fixtures.md) |
| 1 | 03 | Identity, authorization, reference rates, and resilient FX | [03](./03_sdd_identity-authorization-reference-rates-and-resilient-fx.md) |
| 2 | 04 | Assignors, receivables, authoritative simulation, and quotes | [04](./04_sdd_assignors-receivables-authoritative-simulation-and-quotes.md) |
| 2 | 05 | Settlement preview and atomic idempotent settlement | [05](./05_sdd_settlement-preview-and-atomic-idempotent-settlement.md) |
| 2 | 06 | Whole-settlement reversal, append-only audit, and ledger | [06](./06_sdd_whole-settlement-reversal-append-only-audit-and-ledger.md) |
| 3 | 07 | Frontend authentication and mandatory live simulation | [07](./07_sdd_frontend-authentication-and-mandatory-live-simulation.md) |
| 3 | 08 | Frontend preview, settlement intent, and reversal ledger | [08](./08_sdd_frontend-preview-settlement-intent-and-reversal-ledger.md) |
| 3 | 09 | Observability and reviewer-ready runtime hardening | [09](./09_sdd_observability-and-reviewer-ready-runtime-hardening.md) |
| 3 | 10 | Deterministic E2E, performance evidence, and security gates | [10](./10_sdd_deterministic-e2e-performance-evidence-and-security-gates.md) |
| 4 | 11 | Staff artifacts and reviewer documentation | [11](./11_sdd_staff-artifacts-and-reviewer-documentation.md) |
| 4 | 12 | Authorized collaboration, crisis, publication, and release evidence | [12](./12_sdd_authorized-collaboration-crisis-publication-and-release-evidence.md) |

## Review gates

1. Foundation: 01–02
2. Core backend: 03–04
3. Financial integrity and ledger: 05–06
4. Operator UI: 07–08
5. Operations and release readiness: 09–11
6. Authorization-gated publication: 12

Do not cross a red financial-integrity or security gate. Local commits are green and atomic; all push/PR/merge/publication/tag operations require the explicit Prompt 12 authorization gates.
