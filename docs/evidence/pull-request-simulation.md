# Pull-request simulation review record

Local simulation — not a hosted pull request

Remote mutations: none

## Source and target

Source candidate: `fix/case-brief-conformance` at `a29cfd8b8960a636da6eea6abcbfa44d417814b4`

Target base: `main` at `513cc6bc8ffdf18eb698eea3fba82830ef56bd7d`

The source SHA is the reviewed implementation candidate. This evidence record is a
follow-up documentation commit so that the record can identify an immutable source
without referring to itself.

## Scope

The candidate was reviewed as a senior/staff-level delivery across the complete
system boundary:

- backend authorization, authentication throttling, audit persistence, reporting,
  idempotency, and migration behavior;
- frontend ledger loading, empty, error, retry, filtering, pagination, and safe
  diagnostic behavior;
- PostgreSQL schema invariants, time-zone provenance, retention semantics, query
  plans, and upgrade compatibility;
- container entrypoints, operational documentation, traceability, security checks,
  and executable evidence contracts.

## Verification

The reviewed source passed the repository's integrated coverage gate. Backend risk
coverage was 98.36% lines, 99.56% branches, and 97.98% methods. Frontend coverage
was 95.47% statements, 95.19% branches, 96.45% functions, and 96.39% lines.

The frontend suite passed 110 tests together with type checking, linting,
formatting, and a production build. The real PostgreSQL migration integration suite
passed 12 tests. Documentation, architecture, traceability, reporting evidence,
container-build-input, redaction, role-matrix, Compose rendering, fixed E2E, and
security contracts were also exercised.

## Security

Login throttling now keeps independent canonical account and source budgets, does
not evict active buckets, fails closed at capacity, and has endpoint-level recovery
coverage. Frontend failures pass only through a payload-free logging boundary. The
role matrix treats unexpected server errors as failures, and secret and dependency
scans are included in the release gate.

## Migrations

Migration V23 preserves application-authored legacy instants using one explicitly
configured legacy writer zone while retaining UTC provenance for immutable Flyway
seed rows. Configuration precedence and invalid-zone failure behavior are tested.
All Compose services capable of running Flyway receive the optional override.

Completed idempotency records may be deleted by the documented retention process;
in-flight records and identity-defining mutations remain protected. The schema
inventory, diagrams, ADRs, runbook, and PostgreSQL evidence describe these exact
semantics.

## Rollback

Operators must stop writers, take a verified database backup, and use the documented
maintenance window before applying V23. Binary rollback is safe only before the
migration. After V23, rollback requires restoring the pre-migration backup or an
explicitly reviewed reconciliation, followed by reverting the application changes.
Deleting a completed idempotency key intentionally ends its replay guarantee.

## Residual risks

- V23 requires one known historical writer zone. Mixed-zone legacy data must be
  reconciled before the migration.
- Local JWT signing and the process-local login limiter are exercise-scale
  implementations, not distributed production controls.
- Completed-idempotency retention trades historical replay guarantees for bounded
  storage.
- Representative query-plan evidence uses the documented fixture scale; sustained
  production-throughput validation remains an environment-specific activity.
- Hosted review, release tagging, and public-repository publication require remote
  authority and are deliberately outside this local simulation.

## Review conclusion

The independent standards review reported no findings against repository guidance.
The independent specification review reported no locally actionable gaps in the
senior/staff case brief. The remaining items are explicit deployment or publication
decisions rather than defects in the reviewed candidate.
