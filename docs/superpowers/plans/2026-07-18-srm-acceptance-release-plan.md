# SRM Acceptance, Release, and Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Supply real PostgreSQL/Cucumber/browser/performance/security evidence, local/CI command parity, and documentation that matches observed implementation truth.

**Architecture:** Exercise stabilized application contracts through a real Spring HTTP server, PostgreSQL Testcontainers, and the Compose browser path. Generate deterministic evidence with explicit environment metadata; then make `release-check` aggregate every local gate used by CI.

**Tech Stack:** Cucumber JVM 7.34.3, JUnit Platform Suite 5.14.2, Spring Boot Testcontainers, PostgreSQL 16, Playwright Test, Docker Compose, Trivy, Gitleaks, Syft/CycloneDX, Mermaid CLI, Bash, GitHub Actions.

## Global Constraints

- Cucumber and Playwright exercise production adapters; no mocked financial services.
- Feature and browser tests use stable SDD scenario IDs.
- Use event/state polling and web-first assertions, never fixed sleeps.
- Query-plan evidence records PostgreSQL version, dataset shape, command, plan, and limitations.
- Security suppressions are specific, reviewed, and documented; `ignore-unfixed: true` is not a blanket policy.
- Documentation reconciliation occurs only after runtime gates pass.
- No remote publication, tag, release, or repository visibility action.

---

### Task 1: Create the executable Cucumber suite

**Files:**
- Create: `backend/src/integrationTest/java/com/srm/creditengine/cucumber/RunCucumberTest.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/cucumber/CucumberSpringConfiguration.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/cucumber/PostgresContainerConfiguration.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/cucumber/ScenarioState.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/cucumber/ApiSteps.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/cucumber/DatabaseSteps.java`
- Create: `backend/src/integrationTest/resources/features/srm_acceptance.feature`
- Modify: `backend/src/main/java/com/srm/creditengine/shared/runtime/FixtureLoader.java`

**Interfaces:**
- Produces: JUnit Platform suite selecting classpath resource `features` with glue `com.srm.creditengine.cucumber`.
- Produces: one Spring Boot random-port context and one Spring-managed PostgreSQL container per cached context.
- Produces: scenario-scoped HTTP response, actor token, generated IDs, and before/after row counts.

- [ ] **Step 1: Add the suite entry point**

Create:

```java
package com.srm.creditengine.cucumber;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.srm.creditengine.cucumber")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,summary,json:build/reports/cucumber.json")
public class RunCucumberTest {}
```

- [ ] **Step 2: Create one Spring/Testcontainers context**

Use exactly one glue-path configuration class:

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "srm.clock.fixed-instant=2030-01-15T12:00:00Z")
@Import(PostgresContainerConfiguration.class)
class CucumberSpringConfiguration {}
```

Create a Spring-managed service connection:

```java
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
```

Do not annotate the container with JUnit `@Container`; Spring owns its lifecycle with the cached application context.

- [ ] **Step 3: Define isolated scenario state**

Create a `@ScenarioScope` component holding only the current actor/token, request body, response status/body/headers, generated domain IDs, and scoped database counts. Clear collections in a Cucumber `@Before` hook; do not use static mutable state.

- [ ] **Step 4: Add stable feature scenarios**

Create one feature containing these scenario names exactly:

```gherkin
Feature: SRM executable acceptance

  Scenario: AUTH-003 complete endpoint role matrix
  Scenario: FX-003 direct inverse identity and exact freshness boundary
  Scenario: FX-004 transient-only retry and circuit recovery
  Scenario: PRICE-001 independent invoice and cheque exact vectors
  Scenario: QUOTE-005 immutable exact quote roundtrip
  Scenario: SETTLE-006 same-key concurrency returns one exact settlement
  Scenario: SETTLE-ROLLBACK-008 fault rollback leaves no financial rows
  Scenario: REVERSE-007 whole reversal is terminal and idempotent
  Scenario: REPORT-REV-003 signed ledger filters and stable pagination
  Scenario: OBS-003 logs and metrics contain bounded safe labels
```

Each scenario includes concrete Given/When/Then steps with fixed values and exact decimal strings. Reuse no generated expected amount from production code.

- [ ] **Step 5: Implement real HTTP steps**

`ApiSteps` uses `TestRestTemplate` and the random port to log in, set bearer authorization and idempotency headers, send JSON strings, and capture exact response bytes and headers. It must expose step methods for role login, pricing simulation, Receivable/Quote creation, Preview, Settlement, Reversal, Statement, and expected Problem Detail codes.

Use this request shape for financial values:

```java
Map.of(
    "faceAmount", "1000.00",
    "faceCurrency", "BRL",
    "productType", "MERCANTILE_INVOICE",
    "dueDate", "2030-02-14",
    "settlementCurrency", "USD")
```

- [ ] **Step 6: Implement scoped database assertions**

`DatabaseSteps` uses `JdbcTemplate` and generated IDs to assert row counts, statuses, immutability failures, signed movements, and before/after rollback counts. No assertion uses an unfiltered global count.

- [ ] **Step 7: Make E2E fixtures real and idempotent**

In `FixtureLoader`, insert the fixed Assignor `00000000-0000-0000-0000-000000000201` with `on conflict (id) do update` limited to the deterministic fixture fields. Preserve the existing fixed FX row and fixture records. Running the loader twice must leave one Assignor and one FX observation.

- [ ] **Step 8: Run Cucumber**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*RunCucumberTest'
```

Expected: all ten named scenarios execute and pass; `backend/build/reports/cucumber.json` contains their IDs.

- [ ] **Step 9: Commit executable API acceptance**

```bash
git add -- backend/src/integrationTest/java/com/srm/creditengine/cucumber backend/src/integrationTest/resources/features/srm_acceptance.feature backend/src/main/java/com/srm/creditengine/shared/runtime/FixtureLoader.java
git commit -m "test(acceptance): execute SRM API scenarios"
```

### Task 2: Add the real Playwright critical path

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/global-teardown.ts`
- Create: `frontend/e2e/operator-critical-path.spec.ts`
- Modify: `compose.yaml`

**Interfaces:**
- Produces: `npm --prefix frontend run test:e2e`.
- Produces: scenario `E2E-001` against `http://127.0.0.1:8088`.

- [ ] **Step 1: Install Playwright Test and Chromium**

```bash
npm --prefix frontend install --save-dev @playwright/test
npm --prefix frontend exec -- playwright install chromium
```

Add:

```json
"test:e2e": "playwright test",
"test:e2e:headed": "playwright test --headed"
```

- [ ] **Step 2: Configure Compose-backed browser tests**

Create:

```ts
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  globalTeardown: "./e2e/global-teardown.ts",
  use: {
    baseURL: "http://127.0.0.1:8088",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  webServer: {
    command: "docker compose up --build",
    cwd: "..",
    url: "http://127.0.0.1:8088",
    reuseExistingServer: false,
    timeout: 180_000,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
```

Global teardown executes `docker compose down -v --remove-orphans` and fails visibly if cleanup fails.

- [ ] **Step 3: Ensure Compose has deterministic E2E actors/fixtures**

Add fixed local-only OPERATOR and ADMIN credentials through environment values already consumed by the dev seeder. Ensure the fixture profile loads the fixed Assignor/FX exactly once before the browser starts; do not expose a reset endpoint.

- [ ] **Step 4: Implement `E2E-001`**

The test performs:

```text
login as OPERATOR
enter fixed pricing inputs and observe a live server simulation response
register one Receivable with the fixed Assignor
create one Quote and inspect its complete breakdown
select the Quote and request a fresh Preview
capture the outgoing Settlement request and assert one Idempotency-Key header
confirm and observe the Settlement ID
navigate ledger filters through the UI and assert URL restoration
reload with a deliberately delayed response and prove current filters win
login as ADMIN, reverse the Settlement through the API if no UI reversal control exists
assert positive Settlement and negative Reversal rows link to the original Settlement
```

Use `page.waitForResponse`, `expect(locator).toBeVisible()`, `toHaveText`, and `toHaveCount`. Do not use `waitForTimeout`, `networkidle`, or immediate `isVisible()` assertions.

- [ ] **Step 5: Verify network-level idempotency**

Attach a request listener before clicking Confirm:

```ts
const settlementRequest = page.waitForRequest((request) =>
  request.url().endsWith("/api/v1/settlements") && request.method() === "POST",
);
await page.getByRole("button", { name: "Confirm settlement" }).click();
const request = await settlementRequest;
expect(request.headers()["idempotency-key"]).toMatch(/^[0-9a-f-]{36}$/i);
```

Assert a simulated unknown-outcome retry reuses the exact captured key and body.

- [ ] **Step 6: Run Playwright**

```bash
npm --prefix frontend run test:e2e
```

Expected: `E2E-001` passes against real backend, PostgreSQL, and browser; failures retain trace/screenshot/video.

- [ ] **Step 7: Commit browser acceptance**

```bash
git add -- frontend/package.json frontend/package-lock.json frontend/playwright.config.ts frontend/e2e compose.yaml
git commit -m "test(e2e): prove the operator financial path"
```

### Task 3: Capture representative reporting query evidence

**Files:**
- Create: `scripts/sql/representative_statement_dataset.sql`
- Create: `scripts/sql/explain_settlement_statement.sql`
- Create: `scripts/explain-statements-representative.sh`
- Create: `docs/evidence/reporting-explain.txt`
- Modify: `Makefile`

**Interfaces:**
- Produces: `make explain-statements-representative`.

- [ ] **Step 1: Create a deterministic transaction-scoped dataset**

The dataset SQL inserts fixed reference rows plus 10 assignors, 10,000 Receivables/Quotes/Settlements/items, and Reversals for every tenth Settlement using `generate_series`. Use fixed UUIDs derived from MD5 text, fixed timestamps spanning 30 days, and valid FK values. Wrap generation and EXPLAIN in one transaction ending with `rollback` so reviewer data is not retained.

- [ ] **Step 2: Explain the exact production query shape**

Copy the SQL union, filters, ordering, limit, and offset from `JdbcSettlementStatementService`. Execute:

```sql
explain (analyze, buffers, settings, wal, format text)
select ...
where effective_at >= timestamp '2030-01-10T00:00:00Z'
  and effective_at < timestamp '2030-01-20T00:00:00Z'
  and settlement_currency_code = 'BRL'
order by effective_at desc, entry_id desc
limit 51 offset 0;
```

- [ ] **Step 3: Capture reproducible metadata and plan**

`scripts/explain-statements-representative.sh` writes `docs/evidence/reporting-explain.txt` containing UTC capture time, `select version()`, row counts by table, dataset SQL checksum, exact query, plan, and a statement that 10,000 rows are representative evidence rather than a 1M/min production claim.

The script starts only the PostgreSQL service with `docker compose up -d --wait postgres`, installs a trap that runs `docker compose down -v --remove-orphans`, and invokes `psql` inside the container. It must work after `verify-compose` has already torn down the stack.

- [ ] **Step 4: Add and run the Make target**

```make
explain-statements-representative:
	./scripts/explain-statements-representative.sh
```

Run:

```bash
make explain-statements-representative
```

Expected: exit 0, plan uses the intended time/dimension indexes or documents a justified PostgreSQL planner decision, and no sequential scan caused by a missing required index remains.

- [ ] **Step 5: Commit query evidence**

```bash
git add -- scripts/sql scripts/explain-statements-representative.sh docs/evidence/reporting-explain.txt Makefile
git commit -m "perf(reporting): capture representative query plan"
```

### Task 4: Complete local and CI security/release gates

**Files:**
- Create: `scripts/security-scan.sh`
- Create: `scripts/validate-traceability.sh`
- Create: `scripts/validate-docs.sh`
- Create: `scripts/test-crisis-evidence.sh`
- Modify: `scripts/inspect-observability.sh`
- Modify: `Makefile`
- Modify: `.github/workflows/ci.yml`
- Modify: `scripts/tests/test_ci_workflow.sh`

**Interfaces:**
- Produces: `security-scan`, `validate-docs`, `validate-traceability`, `test-api-features`, `test-ui-features`, `test-crisis-evidence`, `e2e-fixed`, and `release-check`.

- [ ] **Step 1: Implement the local security aggregate**

`scripts/security-scan.sh` runs pinned container images for:

```text
gitleaks repository scan
Trivy filesystem vulnerability/secret/misconfiguration scan
backend runtime image vulnerability/config scan
frontend runtime image vulnerability/config scan
CycloneDX/Syft SBOM generation
backend and frontend license-check
```

Fail on HIGH/CRITICAL fixed vulnerabilities and every secret finding. For unfixed CVEs, require an exact `.trivyignore.yaml` entry with CVE, image/package, justification, owner, and expiry; remove global `ignore-unfixed: true`.

- [ ] **Step 2: Authenticate observability inspection**

Update `scripts/inspect-observability.sh` to log in with the local ADMIN reviewer account, extract the bearer token without printing it, and call `/actuator/prometheus`. Assert required metric names and reject sensitive or identifier-shaped label values.

- [ ] **Step 3: Implement documentation and traceability validators**

`validate-docs.sh` runs Markdown link checks used by the project, Mermaid rendering, migration-to-ER checks, OpenAPI/schema checks, and forbidden claim scans. `validate-traceability.sh` parses every SDD scenario ID and fails when it lacks a feature/test/script path and command in `docs/REQUIREMENT_TRACEABILITY.md`.

- [ ] **Step 3A: Implement the disposable crisis/revert proof**

`scripts/test-crisis-evidence.sh` creates a temporary shared clone from the current local `HEAD`, creates `simulation/crisis-revert`, appends `exit 99` to the clone's architecture-document check, commits that controlled regression, proves the check fails, reverts the regression with `git revert --no-edit`, proves the check passes, and records the failing/revert commit hashes under the temporary clone. A trap removes the clone. The script never pushes, tags, touches the working repository, or treats the simulated commits as source history.

Core assertions:

```bash
if ./scripts/tests/test_architecture_docs.sh; then
  echo "crisis simulation failed: injected regression passed" >&2
  exit 1
fi
git revert --no-edit HEAD
./scripts/tests/test_architecture_docs.sh
test "$(git rev-list --count HEAD~2..HEAD)" -eq 2
```

- [ ] **Step 4: Define real Make targets**

Add:

```make
test-api-features:
	./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*RunCucumberTest'

test-ui-features:
	npm --prefix frontend run test:e2e

e2e-fixed: test-api-features test-ui-features

security-scan:
	./scripts/security-scan.sh

validate-docs:
	./scripts/validate-docs.sh

validate-traceability:
	./scripts/validate-traceability.sh

test-crisis-evidence:
	./scripts/test-crisis-evidence.sh

release-check: verify-fast build test-runtime verify-compose e2e-fixed explain-statements-representative security-scan validate-docs validate-traceability test-crisis-evidence
```

- [ ] **Step 5: Make CI call the same aggregates**

Retain pinned actions. Replace the broad Trivy policy, add runtime image scans, install Playwright browsers, upload Cucumber/Playwright/query-plan artifacts, and invoke the same Make targets. Update `scripts/tests/test_ci_workflow.sh` to assert every target is defined and called; it must not merely grep job names.

- [ ] **Step 6: Run non-documentation release gates**

```bash
make test-api-features
make test-ui-features
make explain-statements-representative
make security-scan
make test-crisis-evidence
```

Expected: all exit 0. Documentation and traceability targets run after Task 5 reconciles their source files.

- [ ] **Step 7: Commit release command parity**

```bash
git add -- scripts/security-scan.sh scripts/validate-traceability.sh scripts/validate-docs.sh scripts/test-crisis-evidence.sh scripts/inspect-observability.sh scripts/tests/test_ci_workflow.sh Makefile .github/workflows/ci.yml
git commit -m "build(release): align local and CI evidence gates"
```

### Task 5: Reconcile reviewer documentation after runtime success

**Files:**
- Modify: `README.md`
- Modify: `docs/RUNBOOK.md`
- Modify: `docs/PERMISSION_MATRIX.md`
- Modify: `docs/REQUIREMENT_TRACEABILITY.md`
- Modify: `docs/SRM_REQUIREMENTS_PLAN.md`
- Modify: `docs/architecture/er-diagram.mmd`
- Modify: `docs/architecture/c4-container.mmd`
- Modify: `HT_USAGE.md`
- Modify: `AI_USAGE.md` if present

**Interfaces:**
- Consumes: fresh outputs from Tasks 1–4.
- Produces: reviewer commands and claim classifications matching those outputs.

- [ ] **Step 1: Update only observed status claims**

For each requirement classify `Implemented`, `Proposed`, or `Gap`, and attach its actual command and artifact path. Remove the old Gradle, frontend build, missing-target, mutable-history, and absent-evidence blockers only after their commands pass.

- [ ] **Step 2: Reconcile architecture artifacts**

Add complete Quote snapshot fields, immutable FX/Reversal annotations, and unique signed ledger movement identity to the ER diagram. Mark runtime components implemented rather than planned only when Compose and Playwright evidence exists. Mermaid files must render.

- [ ] **Step 3: Record honest AI/tool usage**

List actual tools and agent roles used, human review controls, exact-money checks, security review, and limitations. Do not claim human authorship or production-scale proof that did not occur.

- [ ] **Step 4: Run documentation gates**

```bash
make validate-docs
make validate-traceability
```

Expected: PASS with zero broken links, Mermaid failures, schema drift, or unclassified requirements.

- [ ] **Step 5: Commit documentation truth**

```bash
git add -- README.md docs HT_USAGE.md AI_USAGE.md
git commit -m "docs: reconcile SRM reviewer evidence"
```

Stage only files that exist and were intentionally updated; never use `git add docs` if unrelated docs are dirty.

### Task 6: Run the integrated release gate

- [ ] **Step 1: Run the full local aggregate**

```bash
make release-check
```

Expected: exit 0 with backend/frontend builds, PostgreSQL integration, Compose, Cucumber, Playwright, query plan, security, and documentation gates all executed.

- [ ] **Step 2: Inspect generated evidence**

Confirm Cucumber JSON contains all named scenarios, Playwright report contains `E2E-001`, reporting evidence records PostgreSQL 16 and 10,000 rows, scans identify the exact runtime image digests, and no artifact contains credentials or tokens.

- [ ] **Step 3: Request independent review**

Dispatch verifier, security reviewer, and code reviewer. Any confirmed blocker returns to its owning product/evidence plan and repeats the focused command before `make release-check` runs again.
