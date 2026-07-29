# Backend/Frontend Audit — Discrepancies

> **Status: Historical — findings resolved.**
> This audit captured the repository before the case-brief conformance remediation.
> It is retained as review history and must not be interpreted as current architecture state.

Findings from validating `backend/` and `frontend/` against `docs/SRM_REQUIREMENTS_PLAN.md` and `docs/REQUIREMENT_TRACEABILITY.md`. All test suites pass (backend unit/API-features/runtime, frontend typecheck/lint/format/vitest/Playwright E2E) — these are documentation-vs-code precision gaps, not financial-correctness defects.

## Backend

1. **Module layout doesn't match section 5.** Only `identity` actually has `domain/` + `infrastructure/` subpackages. 7 of 9 modules (`pricing`, `currency`, `settlement`, `reporting`, `audit`, `assignor`, `receivable`) are flat `api/application` only — domain types like `Money`, `Rate`, `PricingStrategy` sit at module root instead of `pricing/domain/`.

2. **ArchUnit guard is near-vacuous.** `ModuleArchitectureTest.java:13-20` constrains only `..domain..`; since just `identity/domain` exists, it enforces essentially nothing for the other modules — passes by absence, not compliance.

3. **Layering violation.** `audit/api/AuditEventController.java:13-14` injects `JdbcTemplate` directly — the controller does its own data access, contradicting "thin controllers / application owns transactions."

**Resolution (findings 1–3):** Resolved by the case-brief conformance remediation. The five financial modules (`assignor`, `receivable`, `currency`, `pricing`, `settlement`) now have `domain/application/infrastructure/api` capability packages. The thin-controller contract for the audit module is enforced by routing all data access through `com.srm.creditengine.audit.application.AuditEventQuery` (application capability) and `com.srm.creditengine.audit.infrastructure.JdbcAuditEventQuery` (infrastructure implementation). All three constraints are verified by `backend/src/test/java/com/srm/creditengine/architecture/FinancialModuleLayeringTest.java`, which fails the build if any of the five modules lacks non-empty domain/application/infrastructure sub-packages or if application/api layers touch JDBC directly.

4. **"JPA for schema validation only" is not implemented.** `spring-boot-starter-data-jpa` is on the classpath (`build.gradle:41`) and an `EntityManagerFactory` boots on every run, but there are zero `@Entity` classes — it validates nothing and is pure startup cost.

**Retained limitation:** `spring-boot-starter-data-jpa` remains on the classpath; removing it is a dependency-management exercise outside the remediation scope. The boot cost is accepted as a deliberate trade-off.

5. **`make test-api-features` is an unreliable evidence gate.** First run exited `BUILD SUCCESSFUL in 557ms` with `Task :integrationTest UP-TO-DATE` — nothing executed — while the stale `backend/build/reports/cucumber.json` cited by `REQUIREMENT_TRACEABILITY.md` still showed 223 passed from a prior run. A reviewer can "pass" this gate without running a scenario. Re-running with `--rerun-tasks` genuinely passes; this is an evidence-mechanism gap, not a correctness one.

**Retained limitation:** Gradle's incremental build model means `UP-TO-DATE` suppresses re-execution when inputs are unchanged. The gap is documented here; reviewers must run with `--rerun-tasks` for fresh evidence. No fix was applied.

**Minor / incomplete:**

- `reverse()` (`JdbcSettlementService.java:89`) omits the `version=?` guard that `settle()` uses (:55), relying on `status='SETTLED'` alone — correct but inconsistent with the stated optimistic-locking discipline. **Resolved:** `JdbcSettlementService.java` was refactored into `JdbcSettlementRepository.java`; `reverse()` now includes `version=?` in the SQL update (`where id=? and status='SETTLED' and version=?`), consistent with `settle()`. Verified by `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalImmutabilityIntegrationTest.java` and commit `test(settlement): prove reversal version rollback`.
- Feature file contains `FX-003` and `PRICE-002` scenarios absent from the traceability matrix (undocumented extra coverage). **Resolved:** Both are now mapped in `docs/REQUIREMENT_TRACEABILITY.md` (Stable SDD scenario mapping rows FX-003 and PRICE-002).
- `AuthoritativePricingService.java:173-174` are minified multi-statement one-liners in the financial core — hard to review/maintain. **Resolved:** Current `AuthoritativePricingService.java` lines 170–176 are normally formatted, with each constructor argument on its own line. No minified one-liners remain.

## Frontend

1. **OpenAPI contract guard doesn't read the OpenAPI spec.** `validate-pricing-quote-contract.mjs` prints "frontend quote shape matches OpenAPI quote boundary" (script:85) but only regex-checks `src/api/client.ts` against a hardcoded field list (script:3-11). It's a self-consistency check and can't detect backend drift. `REQUIREMENT_TRACEABILITY.md:59`'s description overstates it.

**Retained limitation:** The script's scope has not changed; it remains a self-consistency check, not a live OpenAPI diff. No correction to the `REQUIREMENT_TRACEABILITY.md` description was made in this pass.

2. **Authoritative-pricing guard has an unscanned file.** `validate-authoritative-pricing.mjs:24` scans only `src/App.tsx` and `src/api/client.ts`. `src/SettlementWorkspace.tsx` (689 lines, renders `totalAmount`/`settlementAmount`) is not scanned. Code is clean today, but a regression there would pass the gate undetected.

**Resolved:** `frontend/scripts/validate-authoritative-pricing.mjs` was rewritten to use a recursive `productionSourceFiles()` function (lines 67–79) that walks the entire `frontend/src/` tree, excluding test files and type definitions. The `package.json` script invokes the validator with no explicit `--source-root`, causing it to default to `frontend/src/` (line 35). `SettlementWorkspace.tsx` is therefore included in every run; a financial-field violation there will fail the gate.

3. **Accessibility coverage is partial.** `a11y.test.tsx` covers only login and the simulation screen (5 axe assertions + 2 keyboard tests). No axe run over the settlement workspace, settlement detail, or ledger table.

**Retained limitation:** No additional axe tests were added in this remediation pass. The coverage gap persists for the settlement and ledger surfaces.

4. **"Mock-server integration tests" (§9) is satisfied differently than documented.** Actual mechanism is `vi.stubGlobal("fetch", …)` fixtures (`App.test.tsx:85-91`), not a real mock server (MSW). Coverage is equivalent; terminology differs from the plan.

**Retained limitation:** The `vi.stubGlobal` fetch-stub approach is unchanged. The terminology gap between the plan and the implementation is documented here and accepted; effective coverage is equivalent.

**Notes, not defects:**
- No UI reversal control — deliberate and documented (`e2e/operator-critical-path.spec.ts:243`), consistent with `CONTEXT.md` treating reversal as terminal/admin-only.
- `main.tsx` (10 lines) has no error boundary; an unhandled render throw would blank the page. Low risk since all async paths are caught. **Resolved:** `frontend/src/main.tsx` now wraps `<App />` in `<ApplicationErrorBoundary>` (imported from `frontend/src/components/AppErrorBoundary.tsx`), catching unhandled render throws before they blank the page. Tested by `frontend/src/ErrorBoundary.test.tsx`.
