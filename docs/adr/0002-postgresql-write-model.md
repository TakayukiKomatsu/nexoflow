# ADR 0002: PostgreSQL authoritative write model

## Status

Accepted — 2026-07-18.

## Context

The write model must prevent duplicate Settlement, preserve immutable financial
snapshots, enforce actor-scoped idempotency, and roll back a multi-item batch as
one unit under concurrency. Those invariants require conditional updates,
uniqueness, foreign keys, exact decimals, and auditable schema evolution.

## Decision

Use PostgreSQL 16 as the authoritative system of record. Spring JDBC owns the
transactional command path; Flyway SQL and Java migrations are the only schema
authority. Constraints and optimistic versions are final safeguards beneath
application validation. PostgreSQL-only immutability triggers protect completed
history, with Testcontainers providing the authoritative integration evidence.

V23 converts the V1–V22 timezone-less instant columns by interpreting
application-authored wall values in one explicit legacy application zone. The
zone is selected from the `srm.migration.v23.legacy-time-zone` JVM property,
then `SRM_MIGRATION_V23_LEGACY_TIME_ZONE`, then the JVM default. The migration
validates and applies it to its PostgreSQL transaction. Six immutable
reference-rate rows authored by Flyway have stable IDs and retain their UTC
literal provenance. An upgrade from data written in multiple application zones
requires a separate data-reconciliation plan; V23 does not guess per row.

## Alternatives considered

- Document/NoSQL write store: rejected because reconstructing relational
  uniqueness and multi-row ACID behavior in application code increases risk.
- Event store as the initial authority: rejected because projection, replay,
  schema-evolution, and operational requirements exceed the exercise need.
- H2 as a runtime store: rejected; it remains isolated to fast tests and cannot
  prove PostgreSQL locking, JSONB, or PL/pgSQL triggers.

## Consequences

- Positive: database and application invariants fail together and can be tested
  under real PostgreSQL concurrency.
- Positive: SQL migrations and the ER inventory provide reviewable change
  history.
- Negative: write throughput is bounded by a primary/partition, and Java
  migrations create a PostgreSQL-specific operational dependency.
- Negative: V23's `ALTER COLUMN ... TYPE` operations take heavyweight table
  locks and can rewrite populated tables.
- Mitigation: the proposed scale design partitions by ownership key and uses an
  outbox rather than weakening authoritative consistency.
- Mitigation: run V23 with writers stopped in a planned maintenance window,
  after backup and legacy-zone verification; the real PostgreSQL upgrade test
  covers a non-UTC writer and a changed upgrade-host zone.

## Revisit triggers

Revisit physical topology when measured peak write utilization remains above
70%, lock/replication lag threatens SLOs, or data residency requires isolation.
Any shard or alternative store must retain actor-scoped idempotency, exact
decimal semantics, immutable audit history, online migration, reconciliation,
and a tested RPO/RTO before it can become authoritative.
