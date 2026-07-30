# Git workflow

This project's intended **GitHub Flow** uses short-lived `feature/<topic>` branches, rebases them only while unpublished, and requires pull-request review and required checks before integration into `main`. This fits a small delivery team because it keeps one releasable integration branch without the long-lived coordination overhead of Git Flow. The initial publication described below deviated from the PR-before-main rule.

## Rules

- Never work directly on `main`.
- Use Conventional Commits and focused, green, atomic commits.
- Install local hooks with `make install-hooks`; emergency bypasses require a follow-up issue and explanation in the pull request.
- Before first push, autosquash local `fixup!` commits and use interactive rebase to leave a coherent history. Never rebase or force-push a reviewed/shared branch.
- A pull request records scope, tests, security results, migrations, rollback notes, and residual risks. Required checks and review must pass before merge.
- Release tags and repository publication require the explicit authorization gates in SDD 12.

## Hosted publication status and history shape

The assignment remediation was autosquashed and integrated before publication;
rebase evidence is captured in
`docs/evidence/final-remediation-rebase.md`. This demonstrates disciplined
unpublished-branch rebasing; it does not claim that every historical integration
was linear.

The public [Nexoflow repository](https://github.com/TakayukiKomatsu/nexoflow)
exists. Its initial publication was a direct push to `main` at
[`3a01ab7`](https://github.com/TakayukiKomatsu/nexoflow/commit/3a01ab7c97aa9730fb29f4d58e3b84fb8a478cfe).
That direct-main publication deviated from the intended PR-before-main rule. This
cleanup returns the repository to a reviewable pull-request flow.

Hosted CI [run `30502414061`](https://github.com/TakayukiKomatsu/nexoflow/actions/runs/30502414061)
completed with failure. Verify encountered missing Git identity in a disposable
collaboration simulation; License compliance and security scan separately failed
because Ubuntu lacked the `zsh` required by `scripts/with-java21.sh`. The wrapper
is now portable, and the brittle local Git-history simulation has been removed
from the quality gate. Hosted
[cleanup pull request #1](https://github.com/TakayukiKomatsu/nexoflow/pull/1)
is open, but no hosted green run, reviewer approval, merge, new release tag, or
public release has been observed. Historical merge commits `f7e0cf5` and
`1b3f8a8` remain part of the unrevised project history. `v1.0.0` at `af898ef`
remains historical and is not evidence of a new hosted release. The
release-related status remains **Pending hosted evidence**.

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
