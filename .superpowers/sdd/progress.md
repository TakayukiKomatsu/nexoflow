# Case brief conformance execution ledger

Base: `513cc6b` (`docs: plan case brief conformance repair`)
Plan: `docs/superpowers/plans/2026-07-22-case-brief-conformance.md`
Branch: `fix/case-brief-conformance` (worktree `.worktrees/case-brief-conformance`)

Reconstructed 2026-07-23 from `.superpowers/sdd/task-*-report.md` and `git log main..HEAD` (97 commits) after resuming a prior session's work. Ledger had gone stale (header only) despite real progress; git log and reports are the source of truth per skill guidance.

---

Task 1: complete (FinancialModuleLayeringTest exists, architecture contract wired; see task-1-report.md)
Task 2: complete (commit d5aa3d7 `refactor(core): separate assignor and receivable persistence`; assignor/receivable domain+application+infrastructure; see task-2-report.md)
Task 3: complete (currency/pricing domain+application+infrastructure present; FxConversionPolicy, PricingQuoteSnapshot; see task-3-report.md)
Task 4: complete (settlement/domain present: SettlementPolicy, SettlementDraft, LockedQuote, LockedSettlement, exceptions; see task-4-report.md)
Task 5: complete (frontend AppErrorBoundary, Login, PricingForm, PricingBreakdown, StatementLedger, SettlementDetail/Intent components; useLiveSimulation/useSettlementIntent/useStatementFilters/useSettlementDetail/useSessionLifecycle/usePricingWorkflow hooks; see task-5-report.md)
Task 6: complete — implemented under different names than the plan's literal text, same behavior:
  - README has "GitHub Flow rationale" and "Proposed evolution to 1M transactions/minute" sections (commit history around 71633ec/7d306f0/bcae65c)
  - `scripts/test-crisis-evidence.sh` runs in a disposable clone whose branch is renamed `main` (git branch -M main), not `simulation/crisis-revert`
  - `scripts/test-local-collaboration-evidence.sh` (COLLAB-LOCAL-001/REBASE-LOCAL-002) proves disposable-clone interactive autosquash rebase (`git rebase -i --autosquash`) AND writes/validates a local PR-simulation artifact (`.git/PULL_REQUEST_SIMULATION.md` fields: Title/Base/Head/Checks/Rollback/Residual risk), then fast-forwards into disposable main — this single script covers both the plan's separate "rebase-evidence" and "pull-request-simulation.md" asks
  - `docs/evidence/pull-request-simulation.md` exists as the committed artifact
  - Wired into `Makefile`: `verify-fast` depends on `test-local-collaboration-evidence` and `test-crisis-evidence-contract`; `release-check` depends on `test-crisis-evidence`
  - `v1.0.0` tag documentation corrected consistently across README.md, docs/SRM_REQUIREMENTS_PLAN.md, docs/REQUIREMENT_TRACEABILITY.md, docs/SDD_PROMPTS.md, docs/sdd/12_*, AI_USAGE.md: stale historical tag at `af898ef`, not moved/reused, remote publication/new tag stays authorization-gated
  - commits: 90065a9/82cd371 `test(git): prove local review rebase and crisis recovery`, 8ef17f3, b298485, b280299
  - NOT yet verified in this session: full `make release-check` pass on current HEAD (Task 7, in progress below)

Task 7: in progress — running `make release-check` (background); pending: restore `docs/evidence/reporting-explain.txt`, independent final review, merge/finish-branch decision.
