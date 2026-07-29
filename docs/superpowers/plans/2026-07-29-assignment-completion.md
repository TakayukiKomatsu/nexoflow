# Assignment Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining mandatory Senior/Staff assignment gaps and publish a reviewed, CI-verified `v1.1.0` release without rewriting historical evidence.

**Architecture:** Preserve the modular monolith and existing public contracts. Harden only the Reversal persistence seam by carrying the locked Receivable version into the conditional update; reconcile stale evidence; then produce a real rebased remediation branch, hosted PR/CI evidence, and a new immutable release tag. Existing merge commits and `v1.0.0` remain untouched and accurately disclosed.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring JDBC, PostgreSQL 16/Testcontainers, JUnit 5, Mockito, AssertJ, Bash, Make, Git, GitHub Actions, GitHub CLI.

## Global Constraints

- Source requirements: `docs/README_case_dev_srm.md`, especially lines 116–142 and delivery lines 164–168.
- Preserve BRL/USD-only behavior, decimal-string HTTP contracts, `BigDecimal`/PostgreSQL `NUMERIC`, and `HALF_EVEN` money rounding.
- Preserve atomic actor-scoped idempotency, ordered row locks, immutable financial history, whole-Settlement Reversal, and append-only ledger behavior.
- Do not change API payloads, OpenAPI operations, database schema, quote lifetime, or public error codes.
- Do not move, delete, or reuse historical tag `v1.0.0`.
- Do not rewrite existing `main` history or conceal merge commits `f7e0cf5` and `1b3f8a8`.
- Do not fabricate hosted PR, reviewer, CI, protected-branch, tag, release, or public-repository evidence.
- Terraform/Kubernetes, external OIDC/FX, tracing, load testing, and implemented EDA are outside scope.
- Remote mutation requires explicit owner authorization and authenticated credentials.
- Execute in an isolated worktree via `superpowers:using-git-worktrees`; use branch `fix/assignment-completion`.
- Commit messages use Conventional Commits and no co-authored attribution.

---

### Task 1: Carry locked Receivable versions through Reversal

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/settlement/domain/LockedReceivable.java`
- Modify: `backend/src/main/java/com/srm/creditengine/settlement/domain/LockedSettlement.java:1-8`
- Modify: `backend/src/main/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepository.java:88-118`
- Modify: `backend/src/test/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepositoryTest.java:14-172`

**Interfaces:**
- Produces: `public record LockedReceivable(UUID id, long version)`.
- Changes: `LockedSettlement(UUID settlementId, List<LockedReceivable> receivables)`.
- Preserves: `SettlementRepository.lockSettlement(UUID)` and `SettlementRepository.reverse(LockedSettlement, String, Instant, String)` signatures.
- Preserves: global `order by r.id for update of r` lock order.

- [ ] **Step 1: Update the lock-mapping unit test to require ID and version**

Add the import and change `locksReversalReceivablesInTheGlobalUuidOrder` so its mocked lock query returns domain values:

```java
import com.srm.creditengine.settlement.domain.LockedReceivable;

var lower = new LockedReceivable(lowerReceivable, 3L);
var higher = new LockedReceivable(higherReceivable, 7L);
when(jdbc.query(
        startsWith("select r.id,r.version from receivables r"),
        any(RowMapper.class),
        any(Object[].class)))
        .thenReturn(List.of(lower, higher));

LockedSettlement locked = repository.lockSettlement(settlementId);

assertThat(locked.receivables()).containsExactly(lower, higher);
```

Retain the SQL assertion, but require the query to start with `select r.id,r.version from receivables r` and still end with `order by r.id for update of r`.

- [ ] **Step 2: Add a unit test for the optimistic Reversal predicate**

Add Mockito static imports for `eq` and this test:

```java
@Test
void reversalComparesEveryLockedReceivableVersion() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID settlementId = UUID.randomUUID();
    UUID receivableId = UUID.randomUUID();
    var locked = new LockedReceivable(receivableId, 7L);
    when(jdbc.update(startsWith("insert into settlement_reversals"), any(Object[].class)))
            .thenReturn(1);
    when(jdbc.update(
            eq("update receivables set status='REVERSED', version=version+1 "
                    + "where id=? and status='SETTLED' and version=?"),
            eq(receivableId),
            eq(7L)))
            .thenReturn(0);
    var repository = new JdbcSettlementRepository(jdbc);

    assertThatThrownBy(() -> repository.reverse(
            new LockedSettlement(settlementId, List.of(locked)),
            "reason",
            Instant.parse("2030-01-15T12:00:00Z"),
            "operator@srm.local"))
            .isInstanceOf(AlreadyReversedException.class);
}
```

- [ ] **Step 3: Run the focused unit test and observe the expected failure**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test \
  --tests '*JdbcSettlementRepositoryTest'
```

Expected: compilation fails because `LockedReceivable` and `LockedSettlement.receivables()` do not exist, or the SQL expectation fails because Reversal does not bind `version=?`.

- [ ] **Step 4: Add the locked Receivable domain value**

Create `LockedReceivable.java`:

```java
package com.srm.creditengine.settlement.domain;

import java.util.UUID;

/** Receivable identity and version captured while its row lock is held. */
public record LockedReceivable(UUID id, long version) {
}
```

- [ ] **Step 5: Change `LockedSettlement` to carry versioned values**

Replace the record with:

```java
package com.srm.creditengine.settlement.domain;

import java.util.List;
import java.util.UUID;

/** Settlement header and ordered Receivables held under a reversal lock. */
public record LockedSettlement(UUID settlementId, List<LockedReceivable> receivables) {
}
```

- [ ] **Step 6: Load versions under the existing ordered row lock**

In `lockSettlement`, replace `List<UUID> receivableIds` with:

```java
List<LockedReceivable> receivables = jdbc.query(
        "select r.id,r.version from receivables r join settlement_items si on si.receivable_id=r.id "
                + "where si.settlement_id=? order by r.id for update of r",
        (rs, row) -> new LockedReceivable(rs.getObject(1, UUID.class), rs.getLong(2)),
        lockedId);
if (receivables.isEmpty()) {
    throw new IllegalArgumentException("Settlement has no items");
}
return new LockedSettlement(lockedId, receivables);
```

Add the `LockedReceivable` import. Do not change the Settlement-header lock or the existing reversal-existence check.

- [ ] **Step 7: Compare the captured version in every Reversal update**

Replace the Reversal loop with:

```java
for (LockedReceivable receivable : settlement.receivables()) {
    int updated = jdbc.update(
            "update receivables set status='REVERSED', version=version+1 "
                    + "where id=? and status='SETTLED' and version=?",
            receivable.id(),
            receivable.version());
    if (updated != 1) {
        throw new AlreadyReversedException();
    }
}
```

- [ ] **Step 8: Update the existing stale-state unit fixture**

Change the constructor at `JdbcSettlementRepositoryTest.java:169-170` to:

```java
new LockedSettlement(
        UUID.randomUUID(),
        List.of(new LockedReceivable(UUID.randomUUID(), 4L)))
```

- [ ] **Step 9: Run the focused unit tests**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test \
  --tests '*JdbcSettlementRepositoryTest' \
  --tests '*SettlementApplicationServiceClockTest'
```

Expected: `BUILD SUCCESSFUL`; lock-order assertions, version binding, stale-state conflict, and application orchestration remain green.

- [ ] **Step 10: Commit the domain and repository change**

```bash
git add \
  backend/src/main/java/com/srm/creditengine/settlement/domain/LockedReceivable.java \
  backend/src/main/java/com/srm/creditengine/settlement/domain/LockedSettlement.java \
  backend/src/main/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepository.java \
  backend/src/test/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepositoryTest.java
git commit -m "fix(settlement): compare locked versions during reversal"
```

---

### Task 2: Prove Reversal version conflicts roll back atomically in PostgreSQL

**Files:**
- Modify: `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalIntegrationTest.java:1-199`
- Verify: `backend/src/integrationTest/java/com/srm/creditengine/settlement/application/JdbcSettlementServiceConcurrencyIntegrationTest.java:448-488`

**Interfaces:**
- Consumes: `LockedReceivable`, `LockedSettlement`, and `SettlementRepository` from Task 1.
- Proves: a version mismatch after an earlier item update rolls back the Reversal row and every Receivable transition.

- [ ] **Step 1: Add integration-test dependencies**

Add imports:

```java
import com.srm.creditengine.settlement.application.SettlementRepository;
import com.srm.creditengine.settlement.domain.LockedReceivable;
import com.srm.creditengine.settlement.domain.LockedSettlement;
import org.springframework.transaction.support.TransactionTemplate;
```

Add injected fields:

```java
@Autowired SettlementRepository settlementRepository;
@Autowired TransactionTemplate transactions;
```

- [ ] **Step 2: Write a real-PostgreSQL stale-version rollback test**

Add this test before helper methods:

```java
@Test
void reversalVersionConflictRollsBackTheInsertedReversalAndEarlierItemUpdates() {
    UUID assignorId = UUID.randomUUID();
    assignors.create(new AssignorService.CreateCommand(
            assignorId,
            "Version Conflict Co",
            "VCON" + assignorId.toString().substring(0, 8),
            true,
            "operator@srm.local"));
    List<UUID> receivableIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<UUID> quoteIds = receivableIds.stream()
            .map(receivableId -> createQuote(assignorId, receivableId))
            .toList();
    var settlement = settlements.settle(
            quoteIds,
            "version-conflict-settle-" + assignorId,
            "operator@srm.local");

    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
        LockedSettlement locked = settlementRepository.lockSettlement(settlement.settlementId());
        List<LockedReceivable> stale = List.of(
                locked.receivables().get(0),
                new LockedReceivable(
                        locked.receivables().get(1).id(),
                        locked.receivables().get(1).version() - 1));
        settlementRepository.reverse(
                new LockedSettlement(locked.settlementId(), stale),
                "stale version proof",
                java.time.Instant.parse("2030-01-15T12:00:00.123456789Z"),
                "operator@srm.local");
    })).isInstanceOf(AlreadyReversedException.class);

    assertThat(receivableStatuses(receivableIds)).containsExactly("SETTLED", "SETTLED");
    assertThat(jdbc.queryForObject(
            "select count(*) from settlement_reversals where settlement_id=?",
            Integer.class,
            settlement.settlementId())).isZero();
    assertThat(jdbc.queryForList(
            "select version from receivables where id in (?, ?) order by id",
            Long.class,
            receivableIds.get(0),
            receivableIds.get(1))).containsOnly(1L);
}
```

Use ordered UUID fixtures if the random IDs make the existing `order by id` assertions ambiguous; expected state is independent of input order.

- [ ] **Step 3: Run the new PostgreSQL test**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest \
  --tests '*SettlementReversalIntegrationTest'
```

Expected: `BUILD SUCCESSFUL`; the intentionally stale second item causes `AlreadyReversedException`, and transaction rollback leaves zero Reversal rows and both Receivables at version `1`, status `SETTLED`.

- [ ] **Step 4: Re-run the existing lock-order race**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest \
  --tests '*JdbcSettlementServiceConcurrencyIntegrationTest.REVERSE_007*'
```

Expected: `BUILD SUCCESSFUL`; Reversal and overlapping Settlement complete without PostgreSQL deadlock, Reversal wins, and the alternate Settlement conflicts.

- [ ] **Step 5: Commit the PostgreSQL evidence**

```bash
git add backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalIntegrationTest.java
git commit -m "test(settlement): prove reversal version rollback"
```

---

### Task 3: Archive and reconcile the stale audit

**Files:**
- Move: `docs/AUDIT_DISCREPANCIES.md` → `docs/evidence/historical/2026-07-22-audit-discrepancies.md`
- Modify: `scripts/tests/test_architecture_docs.sh`
- Modify: `docs/REQUIREMENT_TRACEABILITY.md`

**Interfaces:**
- Produces: one explicitly historical audit artifact.
- Enforces: no active `docs/AUDIT_DISCREPANCIES.md` can reintroduce resolved findings as current.

- [ ] **Step 1: Add a failing documentation contract**

In `scripts/tests/test_architecture_docs.sh`, add checks equivalent to:

```bash
historical_audit="docs/evidence/historical/2026-07-22-audit-discrepancies.md"
test ! -e docs/AUDIT_DISCREPANCIES.md \
  || { echo "active stale audit must be archived" >&2; exit 1; }
test -s "$historical_audit" \
  || { echo "historical audit evidence is missing" >&2; exit 1; }
grep -Fq 'Status: Historical — findings resolved' "$historical_audit" \
  || { echo "historical audit lacks resolved status" >&2; exit 1; }
grep -Fq 'FinancialModuleLayeringTest.java' "$historical_audit" \
  || { echo "historical audit lacks layering remediation evidence" >&2; exit 1; }
grep -Fq 'AuditEventQuery' "$historical_audit" \
  || { echo "historical audit lacks audit-layer remediation evidence" >&2; exit 1; }
```

Use the script's existing repository-root convention rather than assuming the current directory.

- [ ] **Step 2: Run the architecture-doc contract and observe failure**

```bash
make validate-architecture-docs
```

Expected: failure because the active audit still exists and the historical path does not.

- [ ] **Step 3: Move the audit without losing history**

```bash
mkdir -p docs/evidence/historical
git mv docs/AUDIT_DISCREPANCIES.md \
  docs/evidence/historical/2026-07-22-audit-discrepancies.md
```

- [ ] **Step 4: Mark every finding as historical and resolved**

At the top of the moved document, add:

```markdown
> **Status: Historical — findings resolved.**
> This audit captured the repository before the case-brief conformance remediation.
> It is retained as review history and must not be interpreted as current architecture state.
```

For the former module-layout, near-vacuous ArchUnit, and direct-JDBC controller findings, add a `Resolution` paragraph naming:

- `backend/src/test/java/com/srm/creditengine/architecture/FinancialModuleLayeringTest.java`
- current `domain/application/infrastructure/api` capability packages
- `com.srm.creditengine.audit.application.AuditEventQuery`
- `com.srm.creditengine.audit.infrastructure.JdbcAuditEventQuery`

For every remaining finding, label it either `Resolved` with a concrete current file/test or `Retained limitation` with the current rationale. Do not leave present-tense claims that contradict current code.

- [ ] **Step 5: Add the historical audit to traceability**

Add one evidence row under reviewer/documentation evidence:

```markdown
| Historical audit reconciliation | **Implemented** | `docs/evidence/historical/2026-07-22-audit-discrepancies.md`; resolved findings are retained as historical evidence and guarded from returning as active claims | `make validate-architecture-docs` |
```

- [ ] **Step 6: Run documentation validation**

```bash
make validate-architecture-docs
make validate-traceability
```

Expected: both commands pass; the historical audit is linked, resolved, and no active stale file remains.

- [ ] **Step 7: Commit the evidence reconciliation**

```bash
git add \
  docs/evidence/historical/2026-07-22-audit-discrepancies.md \
  docs/REQUIREMENT_TRACEABILITY.md \
  scripts/tests/test_architecture_docs.sh
git commit -m "docs(audit): archive resolved review findings"
```

---

### Task 4: Align Git claims with real history and prove final-branch rebase discipline

**Files:**
- Modify: `docs/GIT_WORKFLOW.md:1-32`
- Modify: `README.md:5-11,54-65,139-153`
- Modify: `docs/REQUIREMENT_TRACEABILITY.md`
- Create after rebase: `docs/evidence/final-remediation-rebase.md`
- Modify: `scripts/tests/test_local_collaboration_evidence_contract.sh`
- Create: `docs/superpowers/specs/2026-07-29-assignment-completion-design.md`
- Create: `docs/superpowers/plans/2026-07-29-assignment-completion.md`

**Interfaces:**
- Preserves: historical merge commits and `v1.0.0`.
- Produces: exact pre/post-rebase ranges for `fix/assignment-completion`.
- Enforces: documentation never claims the whole repository history is linear.

- [ ] **Step 1: Add a failing truthfulness contract**

Extend `test_local_collaboration_evidence_contract.sh` to require:

```bash
final_rebase_record="$repo_root/docs/evidence/final-remediation-rebase.md"
test -s "$final_rebase_record" \
  || { echo "final remediation rebase record is missing" >&2; exit 1; }
grep -Fq 'Historical merge commits retained: `f7e0cf5`, `1b3f8a8`' "$final_rebase_record" \
  || { echo "final rebase record omits historical merges" >&2; exit 1; }
grep -Eq 'Pre-rebase head: `[0-9a-f]{40}`' "$final_rebase_record" \
  || { echo "final rebase record lacks pre-rebase SHA" >&2; exit 1; }
grep -Eq 'Post-rebase head: `[0-9a-f]{40}`' "$final_rebase_record" \
  || { echo "final rebase record lacks post-rebase SHA" >&2; exit 1; }
grep -Fq 'Range-diff: verified' "$final_rebase_record" \
  || { echo "final rebase record lacks range-diff proof" >&2; exit 1; }
```

- [ ] **Step 2: Run the contract and observe failure**

```bash
make test-local-collaboration-evidence
```

Expected: failure because the final remediation record does not yet exist.

- [ ] **Step 3: Correct the workflow claims before rebasing**

Update `docs/GIT_WORKFLOW.md` and README to state:

- `fix/assignment-completion` will be autosquashed/rebased before first push.
- `f7e0cf5` and `1b3f8a8` are retained historical merge commits.
- The repository demonstrates disciplined unpublished branch rebasing; it does not claim that every historical integration was linear.
- `v1.0.0` is historical and no current release tag exists yet.
- Hosted PR/CI/release evidence remains absent until Task 6.

Update traceability statuses to `Pending hosted evidence` rather than `Implemented` for public repository, hosted PR/review, hosted CI, and final release tag.

- [ ] **Step 4: Commit the truthful workflow wording**

```bash
git add README.md docs/GIT_WORKFLOW.md docs/REQUIREMENT_TRACEABILITY.md \
  docs/superpowers/specs/2026-07-29-assignment-completion-design.md \
  docs/superpowers/plans/2026-07-29-assignment-completion.md \
  scripts/tests/test_local_collaboration_evidence_contract.sh
git commit -m "docs(git): distinguish branch rebase from history shape"
```

- [ ] **Step 5: Preserve the pre-rebase range**

Run before the branch's first push:

```bash
BASE_SHA="$(git merge-base main HEAD)"
PRE_REBASE_HEAD="$(git rev-parse HEAD)"
git branch backup/assignment-completion-pre-rebase "$PRE_REBASE_HEAD"
git log --oneline "$BASE_SHA..$PRE_REBASE_HEAD"
```

Expected: only assignment-completion commits appear. Stop if unrelated commits are present.

- [ ] **Step 6: Autosquash the unpublished branch**

Create `fixup!` commits only for corrections that belong to Tasks 1–4, then run:

```bash
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash "$BASE_SHA"
POST_REBASE_HEAD="$(git rev-parse HEAD)"
test "$PRE_REBASE_HEAD" != "$POST_REBASE_HEAD"
```

Expected: rebase completes without changing the working tree; fixup subjects are absent.

- [ ] **Step 7: Verify semantic equivalence with `range-diff`**

```bash
git range-diff \
  "$BASE_SHA...backup/assignment-completion-pre-rebase" \
  "$BASE_SHA...$POST_REBASE_HEAD" \
  | tee /tmp/assignment-completion-range-diff.txt
test -s /tmp/assignment-completion-range-diff.txt
if git log --format=%s "$BASE_SHA..$POST_REBASE_HEAD" | grep -q '^fixup!'; then
  echo "fixup commit survived autosquash" >&2
  exit 1
fi
```

Review the range-diff; every removed commit must map to an equivalent rewritten commit or an intentional squash.

- [ ] **Step 8: Record the exact rebase evidence**

Generate `docs/evidence/final-remediation-rebase.md` directly from the observed shell variables:

```bash
mkdir -p docs/evidence
{
  printf '# Final assignment remediation rebase evidence\n\n'
  printf 'Local evidence — not a hosted pull request or remote CI run.\n\n'
  printf -- '- Base SHA: `%s`\n' "$BASE_SHA"
  printf -- '- Pre-rebase head: `%s`\n' "$PRE_REBASE_HEAD"
  printf -- '- Post-rebase head: `%s`\n' "$POST_REBASE_HEAD"
  printf -- '- Range-diff: verified\n'
  printf -- '- Fixup subjects after rebase: zero\n'
  printf -- '- Historical merge commits retained: `f7e0cf5`, `1b3f8a8`\n'
  printf -- '- Historical tag retained: `v1.0.0`\n'
} > docs/evidence/final-remediation-rebase.md
grep -Eq 'Base SHA: `[0-9a-f]{40}`' docs/evidence/final-remediation-rebase.md
grep -Eq 'Pre-rebase head: `[0-9a-f]{40}`' docs/evidence/final-remediation-rebase.md
grep -Eq 'Post-rebase head: `[0-9a-f]{40}`' docs/evidence/final-remediation-rebase.md
```

- [ ] **Step 9: Commit and validate the evidence record**

```bash
git add docs/evidence/final-remediation-rebase.md
git commit -m "docs(git): record final remediation rebase evidence"
make test-local-collaboration-evidence
make test-crisis-evidence-contract
```

Expected: both evidence contracts pass.

---

### Task 5: Produce a locally verified release candidate

**Files:**
- Verify: all tracked source, tests, scripts, configuration, and documentation
- Review: diff from `main` to `fix/assignment-completion`

**Interfaces:**
- Produces: one clean candidate branch eligible for first push.
- Does not produce: hosted or release evidence; Task 6 owns remote facts.

- [ ] **Step 1: Run focused regression gates**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test \
  --tests '*JdbcSettlementRepositoryTest' \
  --tests '*FinancialModuleLayeringTest'
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest \
  --tests '*SettlementReversalIntegrationTest' \
  --tests '*JdbcSettlementServiceConcurrencyIntegrationTest'
make validate-architecture-docs
make validate-traceability
make test-local-collaboration-evidence
make test-crisis-evidence-contract
```

Expected: every command passes from a clean checkout with Docker available.

- [ ] **Step 2: Run the complete local release gate**

```bash
make release-check
```

Expected: backend/frontend builds, unit/integration/Cucumber/Playwright tests, PostgreSQL/Compose checks, coverage, security/license/SBOM scans, documentation/traceability gates, query-plan evidence, and crisis proof all pass. A blocked Docker-dependent gate is not a pass.

- [ ] **Step 3: Confirm the candidate contains no unsupported claims**

Run:

```bash
make validate-docs
make validate-traceability
```

Manually confirm README still labels 1M/minute and EDA as proposed, `v1.0.0` as historical, and hosted evidence as pending.

- [ ] **Step 4: Request an independent review of the final branch diff**

Review scope:

- Reversal lock/version correctness and rollback.
- No API/schema drift.
- Accuracy of historical-audit resolutions.
- Accuracy of Git/release claims.
- Test adequacy and release-gate evidence.

Acceptance: no unresolved high-confidence correctness, security, financial-integrity, or specification findings.

- [ ] **Step 5: Apply review fixes as `fixup!` commits and repeat Task 4 rebase evidence**

If review changes any Task 1–4 commit, create a matching `fixup!` commit, repeat Task 4 Steps 5–9, and rerun Step 2. Do not push until review is clean and the final local release gate passes.

- [ ] **Step 6: Record the final local candidate SHA**

```bash
LOCAL_CANDIDATE_SHA="$(git rev-parse HEAD)"
printf 'Local candidate SHA: %s\n' "$LOCAL_CANDIDATE_SHA"
git status --short
```

Expected: no output from `git status --short`. Preserve the SHA for hosted comparison; do not add a tag yet.

---

### Task 6: Publish, review, verify, and release `v1.1.0`

**Files:**
- Modify in a final evidence PR: `README.md`
- Modify in a final evidence PR: `docs/REQUIREMENT_TRACEABILITY.md`
- Modify if needed: `docs/evidence/final-remediation-rebase.md`
- Remote artifacts: GitHub repository, PR, Actions runs, branch protection, annotated tag, release

**Interfaces:**
- Consumes: clean candidate from Task 5.
- Produces: public repository, hosted approval, hosted CI, final main SHA, annotated `v1.1.0`, and published release.
- Authorization inputs:
  - `SRM_GITHUB_REPOSITORY` in `owner/repository` form.
  - Authenticated `gh` session with repository and workflow permissions.
  - Explicit owner approval to create/push the remote, PR, tag, and release.

- [ ] **Step 1: Fail closed if authorization inputs are missing**

```bash
: "${SRM_GITHUB_REPOSITORY:?set owner/repository after owner authorization}"
: "${SRM_RELEASE_VERSION:=v1.1.0}"
test "$SRM_RELEASE_VERSION" = "v1.1.0"
gh auth status
gh repo view "$SRM_GITHUB_REPOSITORY" --json nameWithOwner,url,visibility
```

Expected: authenticated account and the owner-approved repository are displayed. Stop if the repository or visibility differs from the approved target.

- [ ] **Step 2: Configure the remote without altering historical tags**

```bash
REMOTE_URL="git@github.com:${SRM_GITHUB_REPOSITORY}.git"
if git remote get-url origin >/dev/null 2>&1; then
  test "$(git remote get-url origin)" = "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi
git show v1.0.0 --no-patch
test -z "$(git tag --points-at HEAD)"
```

Expected: `origin` is exact, `v1.0.0` still resolves to its historical object, and current HEAD is untagged.

- [ ] **Step 3: Push only the final remediation branch**

```bash
git push -u origin fix/assignment-completion
```

Expected: first push succeeds without force; hooks pass. Do not push `main` or any tag directly at this step.

- [ ] **Step 4: Open the hosted PR**

```bash
gh pr create \
  --repo "$SRM_GITHUB_REPOSITORY" \
  --base main \
  --head fix/assignment-completion \
  --title "fix: complete senior staff assignment evidence" \
  --body-file .github/PULL_REQUEST_TEMPLATE.md
PR_NUMBER="$(gh pr view --repo "$SRM_GITHUB_REPOSITORY" --json number --jq .number)"
PR_URL="$(gh pr view "$PR_NUMBER" --repo "$SRM_GITHUB_REPOSITORY" --json url --jq .url)"
PR_HEAD_SHA="$(gh pr view "$PR_NUMBER" --repo "$SRM_GITHUB_REPOSITORY" --json headRefOid --jq .headRefOid)"
printf 'PR: %s\nHead: %s\n' "$PR_URL" "$PR_HEAD_SHA"
```

Immediately edit the PR body so every template section contains the actual scope, commands, migration impact (`none`), rollback, security result, and residual risks. Do not leave the template's instructional text as evidence.

- [ ] **Step 5: Observe hosted CI and obtain a real review**

```bash
gh pr checks "$PR_NUMBER" --repo "$SRM_GITHUB_REPOSITORY" --watch
gh pr view "$PR_NUMBER" --repo "$SRM_GITHUB_REPOSITORY" \
  --json reviewDecision,mergeStateStatus,statusCheckRollup,reviews
```

Expected:

- every required Actions job succeeds;
- `reviewDecision` is `APPROVED` by a reviewer other than the author;
- `mergeStateStatus` is mergeable;
- no check is skipped because infrastructure was unavailable.

A self-review, local simulation, or green YAML validator does not satisfy this step.

- [ ] **Step 6: Merge using rebase integration and verify tree equivalence**

```bash
gh pr merge "$PR_NUMBER" \
  --repo "$SRM_GITHUB_REPOSITORY" \
  --rebase \
  --delete-branch
git fetch origin main
git switch main
git merge --ff-only origin/main
FINAL_SHA="$(git rev-parse HEAD)"
git diff --exit-code "$PR_HEAD_SHA" "$FINAL_SHA"
printf 'Final main SHA: %s\n' "$FINAL_SHA"
```

Expected: final main tree is byte-equivalent to the approved PR head. Commit IDs may differ because GitHub rebase integration rewrites commits.

- [ ] **Step 7: Wait for push-to-main CI on the exact final SHA**

```bash
gh run list \
  --repo "$SRM_GITHUB_REPOSITORY" \
  --branch main \
  --commit "$FINAL_SHA" \
  --limit 10
gh run watch \
  "$(gh run list --repo "$SRM_GITHUB_REPOSITORY" --branch main --commit "$FINAL_SHA" --limit 1 --json databaseId --jq '.[0].databaseId')" \
  --repo "$SRM_GITHUB_REPOSITORY" \
  --exit-status
```

Expected: the complete push-to-main workflow succeeds for `FINAL_SHA`.

- [ ] **Step 8: Add final hosted links through a second reviewed evidence PR**

Create branch `docs/final-release-evidence` from `origin/main`. Update README and traceability with:

- public repository URL;
- first hosted PR URL and approval status;
- exact final-main CI run URL;
- planned immutable release URL `https://github.com/${SRM_GITHUB_REPOSITORY}/releases/tag/v1.1.0`;
- continued historical status of `v1.0.0`;
- optional IaC and production-hardening omissions.

Use the same PR/check/review process as Steps 3–7. After merging, update `FINAL_SHA` to the evidence PR's final main SHA and wait for push-to-main CI on that exact SHA.

- [ ] **Step 9: Run the complete local release gate on the exact tag candidate**

```bash
git fetch origin main --tags
git switch main
git merge --ff-only origin/main
FINAL_SHA="$(git rev-parse HEAD)"
make release-check
```

Expected: complete local release gate passes on the same `FINAL_SHA` that has green hosted main CI.

- [ ] **Step 10: Create and publish the immutable release**

```bash
test -z "$(git tag --list v1.1.0)"
git tag -a v1.1.0 "$FINAL_SHA" -m "SRM Credit Engine v1.1.0"
test "$(git rev-list -n 1 v1.1.0)" = "$FINAL_SHA"
git push origin v1.1.0
gh release create v1.1.0 \
  --repo "$SRM_GITHUB_REPOSITORY" \
  --verify-tag \
  --title "SRM Credit Engine v1.1.0" \
  --generate-notes
```

Expected: GitHub release exists, its tag dereferences to `FINAL_SHA`, and historical `v1.0.0` remains unchanged.

- [ ] **Step 11: Verify final assignment evidence**

```bash
gh release view v1.1.0 --repo "$SRM_GITHUB_REPOSITORY" --json url,tagName,targetCommitish
gh pr list --repo "$SRM_GITHUB_REPOSITORY" --state merged --limit 10
gh run list --repo "$SRM_GITHUB_REPOSITORY" --branch main --commit "$FINAL_SHA"
git show v1.0.0 --no-patch
git show v1.1.0 --no-patch
```

Acceptance:

- public repository is reachable;
- both hosted PRs show review and green checks;
- main CI is green on `FINAL_SHA`;
- `v1.1.0` and the release point to `FINAL_SHA`;
- `v1.0.0` remains historical;
- local and hosted documents contain no fabricated or stale claims.

---

## Plan self-review result

- **Spec coverage:** Every mandatory Senior/Staff gap identified in `docs/superpowers/specs/2026-07-29-assignment-completion-design.md` maps to Tasks 1–6. Optional IaC and production hardening are explicitly excluded.
- **Variable scan:** Runtime/code steps contain complete types, SQL, tests, and commands. Dynamic SHAs are written directly from observed Git values; the remote repository is supplied through required `SRM_GITHUB_REPOSITORY`, and execution fails closed if it is absent.
- **Type consistency:** `LockedReceivable(UUID id, long version)` is produced in Task 1 and consumed consistently by `LockedSettlement`, repository code, unit tests, and integration tests.
- **Evidence integrity:** Local simulations remain labeled local. Hosted PR, CI, review, tag, and release claims are created only after direct observation.
