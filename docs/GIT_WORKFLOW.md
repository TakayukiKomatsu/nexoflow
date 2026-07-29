# Git workflow

This project uses **GitHub Flow**: short-lived `feature/<topic>` branches are rebased only while unpublished, reviewed through pull requests, and merged into protected `main` after required checks pass. This fits a small delivery team because it keeps one releasable integration branch without the long-lived coordination overhead of Git Flow.

## Rules

- Never work directly on `main`.
- Use Conventional Commits and focused, green, atomic commits.
- Install local hooks with `make install-hooks`; emergency bypasses require a follow-up issue and explanation in the pull request.
- Before first push, autosquash local `fixup!` commits and use interactive rebase to leave a coherent history. Never rebase or force-push a reviewed/shared branch.
- A pull request records scope, tests, security results, migrations, rollback notes, and residual risks. Required checks and review must pass before merge.
- Release tags and repository publication require the explicit authorization gates in SDD 12.

## Executable local collaboration evidence

`make test-local-collaboration-evidence` creates a disposable clone with no
remote, opens a short-lived `feature/local-pr-simulation` branch, records a
review-ready PR description and local PR ref, and runs review checks. It then
performs a real unpublished `git rebase -i --autosquash`, validates the old and
new ranges with `git range-diff`, and fast-forwards the reviewed head into the
clone's `main`. This proves local branch, review-description, clean-history, and
merge mechanics; it is explicitly **not** evidence of a hosted PR, reviewer
approval, protected-branch checks, or publication.

## Current branch status and history shape

`fix/assignment-completion` is an unpublished branch that will be autosquashed with
`git rebase -i --autosquash` before its first push; rebase evidence is captured in
`docs/evidence/final-remediation-rebase.md`. This repository demonstrates disciplined
unpublished-branch rebasing; it does not claim that every historical integration was
linear.

Historical merge commits `f7e0cf5` and `1b3f8a8` are retained as part of the real
project history and have not been rewritten. `v1.0.0` is a historical local tag at
`af898ef`; no current release tag exists. Hosted pull-request, CI, and release
evidence remains absent until Task 6.

## Crisis exercise

`make test-crisis-evidence` clones the release candidate into a disposable
repository, renames that clone's current branch to `main`, commits a harmless
fixture regression, proves the fixture fails, and reverts it. The script then
runs `make verify-fast` and proves the complete reverted tree equals the release
candidate tree. The real repository's `main` is never changed; the branch name
is intentionally realistic only inside the throwaway clone.

## Deliberate deviation: settlement lock ordering

The case-brief conformance refactor (`fix/case-brief-conformance`) moved
settlement persistence into `settlement/infrastructure/JdbcSettlementRepository`
and, while doing so, changed the lock acquisition order from the original
single `for update of q, r` (no explicit ordering) to locking receivables first
in `order by r.id` on both the `settle` and `reverse` paths (commits
`139a0fd`, `3c97ccf`). This was intentional: it closes a latent settle-vs-reverse
deadlock window by making both paths acquire receivable row locks in the same
global order. It is a correctness improvement, not accidental drift, and is
covered by the real-PostgreSQL concurrency race test in `make test-runtime`.

The same refactor's `SettlementPolicy.validateQuotes` also added a
unique-receivables-per-batch check. A batch that references the same
receivable twice now fails fast with `400 INVALID_REQUEST` (malformed request,
caught before any database state is read) rather than the prior `409` from
`AlreadySettledException` (a state conflict discovered against persisted rows).
This is a more precise status code for that case, not a behavior regression.
