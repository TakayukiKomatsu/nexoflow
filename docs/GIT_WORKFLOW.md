# Git workflow

This project uses **GitHub Flow**: short-lived `feature/<topic>` branches are rebased only while unpublished, reviewed through pull requests, and merged into protected `main` after required checks pass. This fits a small delivery team because it keeps one releasable integration branch without the long-lived coordination overhead of Git Flow.

## Rules

- Never work directly on `main`.
- Use Conventional Commits and focused, green, atomic commits.
- Install local hooks with `make install-hooks`; emergency bypasses require a follow-up issue and explanation in the pull request.
- Before first push, autosquash local `fixup!` commits and use interactive rebase to leave a coherent history. Never rebase or force-push a reviewed/shared branch.
- A pull request records scope, tests, security results, migrations, rollback notes, and residual risks. Required checks and review must pass before merge.
- Release tags and repository publication require the explicit authorization gates in SDD 12.

## Crisis exercise

The isolated `simulation/crisis-revert` procedure in SDD 12 creates a harmless regression on a disposable branch, proves it fails, reverts it, and records both hashes. A defective commit is never intentionally merged to `main`.
