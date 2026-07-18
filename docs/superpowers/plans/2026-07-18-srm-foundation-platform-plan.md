# SRM Foundation and Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a buildable, testable, container-capable foundation before product behavior changes begin.

**Architecture:** Create Gradle test configurations before dependency resolution, make frontend quality gates compile, and provide real local/CI targets rather than no-op shims. Use Colima as the Docker-compatible macOS runtime while retaining ordinary Docker behavior in CI.

**Tech Stack:** Gradle, Java 21, Spring Boot 3.5.5, Cucumber JVM 7.34.3, JUnit Platform 5.11.4, Node 26, npm, Colima, Docker Compose, GitHub Actions.

## Global Constraints

- Preserve all unrelated dirty-worktree changes.
- Do not make a verification target pass by skipping its work.
- Keep Java 21, Node 26 CI, PostgreSQL 16, and pinned GitHub Action SHAs.
- Do not disable Testcontainers Ryuk.
- Do not publish commits or tags.

---

### Task 1: Repair the Gradle integration-test model

**Files:**
- Modify: `backend/build.gradle`
- Test: Gradle configuration and focused unit/integration task discovery

**Interfaces:**
- Produces: source set `integrationTest`, configurations `integrationTestImplementation` and `integrationTestRuntimeOnly`, task `integrationTest`.
- Produces: Cucumber 7.34.3 and JUnit Platform 5.11.4 dependencies ready for the acceptance plan.

- [ ] **Step 1: Reproduce the configuration failure**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend tasks --all
```

Expected before the fix: FAIL at `backend/build.gradle:43` because `integrationTestImplementation` does not exist.

- [ ] **Step 2: Move source-set/configuration creation before dependencies**

Use this ordering in `backend/build.gradle`:

```groovy
sourceSets {
    integrationTest {
        java.srcDir file('src/integrationTest/java')
        resources.srcDir file('src/integrationTest/resources')
        compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

dependencies {
    implementation 'org.flywaydb:flyway-core'
    // retain the existing production dependencies

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.1'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

    integrationTestImplementation platform('io.cucumber:cucumber-bom:7.34.3')
    integrationTestImplementation platform('org.junit:junit-bom:5.11.4')
    integrationTestImplementation 'io.cucumber:cucumber-java'
    integrationTestImplementation 'io.cucumber:cucumber-spring'
    integrationTestImplementation 'io.cucumber:cucumber-junit-platform-engine'
    integrationTestImplementation 'org.junit.platform:junit-platform-suite'
    integrationTestImplementation 'org.springframework.boot:spring-boot-testcontainers'
    integrationTestImplementation 'org.testcontainers:junit-jupiter'
    integrationTestImplementation 'org.testcontainers:postgresql'
}
```

Keep the existing `integrationTest` task and add readable scenario names:

```groovy
tasks.register('integrationTest', Test) {
    description = 'Runs PostgreSQL Testcontainers integration contracts.'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty 'cucumber.junit-platform.naming-strategy', 'long'
    shouldRunAfter tasks.named('test')
}
```

- [ ] **Step 3: Prove configuration and task discovery**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend tasks --all
./scripts/with-java21.sh ./backend/gradlew -p backend testClasses integrationTestClasses
```

Expected: both commands exit 0; `integrationTest` appears under Verification tasks.

- [ ] **Step 4: Commit only the build model**

```bash
git add -- backend/build.gradle
git commit -m "fix(build): configure integration tests before dependencies"
```

### Task 2: Restore frontend quality and production build

**Files:**
- Modify: `frontend/src/a11y.test.tsx`
- Test: `frontend/src/a11y.test.tsx`

**Interfaces:**
- Produces: zero unused imports under TypeScript `noUnusedLocals`.

- [ ] **Step 1: Reproduce the typecheck failure**

```bash
npm --prefix frontend run typecheck
```

Expected before the fix: TS6133 for `SIMULATION_DEBOUNCE_MS` at `frontend/src/a11y.test.tsx:4`.

- [ ] **Step 2: Remove only the unused named import**

Change:

```ts
import App, { SIMULATION_DEBOUNCE_MS } from "./App";
```

to:

```ts
import App from "./App";
```

Do not weaken TypeScript or lint configuration.

- [ ] **Step 3: Verify all frontend gates**

```bash
npm --prefix frontend run test -- --run
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

Expected: all commands exit 0.

- [ ] **Step 4: Commit the focused correction**

```bash
git add -- frontend/src/a11y.test.tsx
git commit -m "fix(frontend): restore typecheck and build"
```

### Task 3: Add a real license gate

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/config/allowed-licenses.json`
- Create: `scripts/check-frontend-licenses.mjs`
- Modify: `Makefile`
- Test: `scripts/tests/test_ci_workflow.sh`

**Interfaces:**
- Produces: `npm --prefix frontend run license:check`.
- Produces: `make license-check` used by `.github/workflows/ci.yml`.

- [ ] **Step 1: Add the frontend license dependency and script**

Run:

```bash
npm --prefix frontend install --save-dev license-checker-rseidelsohn
```

Add to `frontend/package.json`:

```json
"license:check": "license-checker-rseidelsohn --production --json --out license-report.json && node ../scripts/check-frontend-licenses.mjs frontend/license-report.json frontend/config/allowed-licenses.json"
```

- [ ] **Step 2: Define the explicit frontend allowlist**

Create `frontend/config/allowed-licenses.json`:

```json
{
  "allowed": [
    "0BSD",
    "Apache-2.0",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "ISC",
    "MIT",
    "Python-2.0"
  ]
}
```

- [ ] **Step 3: Implement exact license validation**

Create `scripts/check-frontend-licenses.mjs`:

```js
import { readFile } from "node:fs/promises";

const [reportPath, policyPath] = process.argv.slice(2);
const report = JSON.parse(await readFile(reportPath, "utf8"));
const policy = JSON.parse(await readFile(policyPath, "utf8"));
const allowed = new Set(policy.allowed);
const rejected = Object.entries(report)
  .filter(([, metadata]) => !String(metadata.licenses ?? "UNKNOWN")
    .split(/\s+OR\s+|\s+AND\s+/)
    .every((license) => allowed.has(license.replace(/[()]/g, ""))))
  .map(([name, metadata]) => `${name}: ${metadata.licenses ?? "UNKNOWN"}`);

if (rejected.length) {
  console.error(`Disallowed production licenses:\n${rejected.join("\n")}`);
  process.exit(1);
}
console.log(`Frontend production licenses approved: ${Object.keys(report).length}`);
```

- [ ] **Step 4: Wire backend and frontend policy into Make**

Add `license-check` to `.PHONY` and define:

```make
license-check:
	./scripts/with-java21.sh ./backend/gradlew -p backend checkLicense
	npm --prefix frontend run license:check
```

If the JK1 plugin exposes `checkLicense` under a different exact task name, confirm with `gradlew tasks --all` and use the actual verification task; do not replace it with report generation alone.

- [ ] **Step 5: Extend the CI contract test and run the gate**

Add assertions to `scripts/tests/test_ci_workflow.sh` that CI calls `make license-check` and the Makefile defines `license-check:`.

Run:

```bash
make license-check
./scripts/tests/test_ci_workflow.sh
```

Expected: both exit 0 and unapproved licenses fail with package name and SPDX expression.

- [ ] **Step 6: Commit the license gate**

```bash
git add -- frontend/package.json frontend/package-lock.json frontend/config/allowed-licenses.json scripts/check-frontend-licenses.mjs Makefile scripts/tests/test_ci_workflow.sh
git commit -m "build: enforce backend and frontend license policy"
```

### Task 4: Establish the local container runtime

**Files:**
- Modify: `scripts/with-java21.sh`
- Workstation configuration: Homebrew/Colima only.

**Interfaces:**
- Produces: working `docker`, `docker compose`, and Testcontainers connectivity on macOS ARM64.

- [ ] **Step 1: Install the runtime tools when absent**

```bash
brew install colima docker docker-compose jq
```

Expected: Homebrew exits 0. If each binary already exists, leave the installed version unchanged.

- [ ] **Step 2: Start Colima with a reachable host address**

```bash
colima start --network-address
```

- [ ] **Step 3: Make Java/Testcontainers commands discover Colima**

Before `scripts/with-java21.sh` executes its command, add:

```bash
if [[ "$(uname -s)" == "Darwin" && -S "${HOME}/.colima/default/docker.sock" ]]; then
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  export TESTCONTAINERS_HOST_OVERRIDE="$(colima ls -j | jq -r '.address')"
fi
```

Do not set `TESTCONTAINERS_RYUK_DISABLED=true`. On Linux CI, the wrapper leaves the ordinary Docker environment untouched.

- [ ] **Step 4: Prove Docker, Compose, and Testcontainers**

```bash
docker info
docker compose version
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*PostgresMigrationIntegrationTest'
```

Expected: Docker reports a Colima Linux kernel; Compose reports a version; PostgreSQL Testcontainers starts and the focused integration test passes.

- [ ] **Step 5: Commit the portable runtime wrapper**

```bash
git add -- scripts/with-java21.sh
git commit -m "build: support Testcontainers through Colima"
```

### Task 5: Re-run the foundation gates

**Files:** none unless a focused correction is required.

- [ ] **Step 1: Run fast verification and builds**

```bash
make verify-fast
make build
```

Expected: both exit 0.

- [ ] **Step 2: Run container-backed baseline tests**

```bash
make test-runtime
```

Expected: unit/runtime and existing integration tests execute; no Gradle configuration error or Docker block remains.
