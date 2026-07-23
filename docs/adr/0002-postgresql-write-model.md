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
- Mitigation: the proposed scale design partitions by ownership key and uses an
  outbox rather than weakening authoritative consistency.

## Revisit triggers

Revisit physical topology when measured peak write utilization remains above
70%, lock/replication lag threatens SLOs, or data residency requires isolation.
Any shard or alternative store must retain actor-scoped idempotency, exact
decimal semantics, immutable audit history, online migration, reconciliation,
and a tested RPO/RTO before it can become authoritative.
