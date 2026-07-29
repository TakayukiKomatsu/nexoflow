# Assignment completion design

**Status:** approved direction; awaiting specification review  
**Date:** 2026-07-29  
**Source of acceptance:** [`docs/README_case_dev_srm.md`](../../README_case_dev_srm.md)  
**Scope:** Close the remaining mandatory Senior/Staff assignment gaps and produce current, externally verifiable submission evidence. Production-hardening work such as external OIDC/FX, tracing, Kubernetes/Terraform, distributed rate limiting, load testing, and implemented EDA is excluded.

## Objective

Make the submitted repository satisfy every mandatory clause in the case brief without rewriting historical evidence, moving the existing `v1.0.0` tag, fabricating hosted results, or turning optional production infrastructure into a release blocker.

## Verified current boundary

The repository already implements the required business system and most Senior/Staff differentiators: Java 21/Spring Boot and React/TypeScript; PostgreSQL/Flyway authority; exact Strategy pricing; mocked FX with retry/circuit behavior; transactional, idempotent Settlement; server-side statement filtering; layered financial modules enforced by ArchUnit; structured logs and bounded Prometheus metrics; C4 L1/L2; CI configuration; Git hooks; ADRs; a safe crisis/revert exercise; and a proposed 1M-transactions/minute EDA evolution.

Focused architecture, workflow, hook, observability, FX, layering, and PostgreSQL Settlement-concurrency checks passed on 2026-07-29. The full `make release-check` was not re-run during that review.

## Remaining mandatory gaps

1. The Reversal path increments Receivable versions but does not compare the version captured under lock, leaving its optimistic-transition discipline inconsistent with Settlement.
2. `docs/AUDIT_DISCREPANCIES.md` contains resolved findings as if they were current, contradicting the present module layout and ArchUnit enforcement.
3. The real Git history contains two merge commits, while the repository demonstrates interactive autosquash only in a disposable clone. Existing history will not be rewritten; the final remediation branch must use the documented rebase workflow and the documentation must distinguish demonstrated practice from whole-history linearity.
4. The local annotated `v1.0.0` tag points to historical commit `af898ef`, not the final submission SHA. It must remain unchanged and must not be presented as the current release.
5. No remote is configured. Hosted PR review, hosted CI, protected-branch evidence, public repository delivery, the final version tag, and release publication are therefore unverified.
6. The committed local PR-simulation record identifies an earlier candidate SHA rather than the final submission SHA. It may remain as local-process evidence, but it cannot serve as final hosted-review evidence.

## Design decisions

### Reversal optimistic transition

Introduce a domain value carrying each locked Receivable's ID and version. `JdbcSettlementRepository.lockSettlement` will select `r.id,r.version` while retaining the existing global `order by r.id for update of r` lock order. `reverse` will update each row with:

```sql
update receivables
set status='REVERSED', version=version+1
where id=? and status='SETTLED' and version=?
```

A zero-row update remains a conflict and raises `AlreadyReversedException`; the surrounding `@Transactional` boundary must roll back the Reversal row and every earlier Receivable update. No API or schema change is required.

### Historical audit reconciliation

Preserve the old audit as historical evidence rather than deleting it. Move it under `docs/evidence/historical/2026-07-22-audit-discrepancies.md`, add a prominent resolved/historical status, and annotate each formerly current finding with the remediation evidence. No active README, runbook, or traceability entry may describe those resolved findings as current.

### Git history and final remediation branch

Do not rewrite `main`, delete merge commits, or move `v1.0.0`. Create one final remediation branch from current HEAD. Keep its commits focused, use `fixup!` only while unpublished, perform real `git rebase -i --autosquash` before first push, verify the rewritten range with `git range-diff`, and integrate it using the repository's documented strategy.

Documentation must state the exact truth: the final branch was rebased before integration; the repository contains two historical merge commits; the repository does not claim that its entire history is linear. This fulfills the requested demonstrated rebase practice without falsifying or destructively rewriting history.

### Hosted review and release evidence

Remote actions require the owner's repository, visibility, credentials, and explicit authorization. Once authorized:

1. Configure the public remote and push the final remediation branch.
2. Open a hosted PR against `main` using `.github/PULL_REQUEST_TEMPLATE.md`.
3. Require all configured CI jobs to pass on the exact candidate SHA.
4. Record an actual reviewer approval and merge using the documented strategy.
5. Run `make release-check` on the resulting final SHA.
6. Create a new annotated `v1.1.0` tag on that exact reviewed SHA; never move or reuse `v1.0.0`.
7. Push the tag and publish the release.
8. Replace local-only final-delivery claims with immutable repository, PR, CI-run, tag, and release URLs.

If remote authorization is withheld, the repository must remain explicitly incomplete for the public-delivery, hosted-review, hosted-CI, and final-tag requirements. Local simulations must not be relabeled as hosted evidence.

## Files and responsibilities

### Runtime and tests

- `backend/src/main/java/com/srm/creditengine/settlement/domain/LockedReceivable.java` — immutable locked Receivable ID/version value.
- `backend/src/main/java/com/srm/creditengine/settlement/domain/LockedSettlement.java` — carries ordered `LockedReceivable` values.
- `backend/src/main/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepository.java` — loads locked versions and enforces version-aware reversal updates.
- `backend/src/test/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepositoryTest.java` — SQL mapping and version-predicate unit contract.
- `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalIntegrationTest.java` — whole-Reversal success and rollback behavior.
- `backend/src/integrationTest/java/com/srm/creditengine/settlement/application/JdbcSettlementServiceConcurrencyIntegrationTest.java` — real PostgreSQL conflict/race evidence.

### Documentation and evidence

- `docs/AUDIT_DISCREPANCIES.md` — move to historical evidence location.
- `docs/evidence/historical/2026-07-22-audit-discrepancies.md` — resolved historical audit with remediation references.
- `docs/GIT_WORKFLOW.md` — distinguish final-branch rebase evidence from whole-history shape.
- `README.md` — current release boundary and final hosted evidence after publication.
- `docs/REQUIREMENT_TRACEABILITY.md` — exact current status and evidence commands/URLs.
- `docs/evidence/pull-request-simulation.md` — retain as local simulation evidence; do not overwrite it with hosted claims.
- `.github/PULL_REQUEST_TEMPLATE.md` — existing hosted review contract; modify only if a required evidence field is missing.

## Verification strategy

### Focused local verification

- Repository unit contract for the new locked-version mapping and SQL arguments.
- Real PostgreSQL concurrent Reversal test proving one winner, one conflict/replay outcome, no partial Receivable state, and one immutable Reversal.
- Existing Settlement concurrency, Reversal immutability, Cucumber role/idempotency, and statement-ledger tests.
- Documentation mutation checks proving resolved audit claims cannot reappear as current.
- Local hook, rebase/range-diff, crisis/revert, architecture, OpenAPI, traceability, and CI-contract validators.

### Final aggregate verification

Run `make release-check` on the exact candidate SHA before hosted review completion and again on the exact final/tagged SHA if the merge result differs. Generated evidence must identify the SHA it verifies; timing-only or machine-local artifacts must not be committed as deterministic proof.

### Hosted verification

The public PR must show all required GitHub Actions jobs green, at least one recorded approval, the final merge SHA, and the annotated `v1.1.0` tag/release pointing to that same reviewed code. Protected-branch settings or their absence must be reported accurately.

## Acceptance criteria

The assignment is complete only when all of the following are true:

1. Reversal uses the locked Receivable version in every status transition and real PostgreSQL tests prove atomic conflict behavior.
2. No active document presents resolved audit findings as current.
3. The final remediation branch has observed autosquash/rebase/range-diff evidence, while historical merge commits are disclosed rather than rewritten.
4. The full local release gate passes on the exact final SHA.
5. A public remote, hosted PR, green hosted CI, recorded review, and public repository satisfy the delivery requirement.
6. A new annotated `v1.1.0` tag and release point to the exact reviewed final code; historical `v1.0.0` remains untouched.
7. README and traceability evidence distinguish implemented runtime, local simulations, hosted facts, proposed scale architecture, and optional omissions.

Optional IaC remains outside acceptance because the case brief marks it optional. Production-hardening work remains outside scope by owner decision.
