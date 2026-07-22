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

## Crisis exercise

`make test-crisis-evidence` clones the release candidate into a disposable
repository, renames that clone's current branch to `main`, commits a harmless
fixture regression, proves the fixture fails, and reverts it. The script then
runs `make verify-fast` and proves the complete reverted tree equals the release
candidate tree. The real repository's `main` is never changed; the branch name
is intentionally realistic only inside the throwaway clone.
