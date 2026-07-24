# ADR 0003: SQL Settlement statement read model

## Status

Accepted — 2026-07-18.

## Context

Settlement statements combine Settlement and Reversal rows, signed amounts,
multiple optional filters, deterministic ordering, and server-side pagination.
Reconstructing write aggregates would add round trips and obscure the query plan.
The exercise also needs representative PostgreSQL evidence without claiming a
production benchmark.

## Decision

Expose a small Reporting query Interface backed by parameterized native SQL.
Derive stable UUID ledger entry identities and signed amounts in the read model,
apply bounded filters/pagination in PostgreSQL, and verify the query shape with a
10,000-row representative dataset. The result is read-only; corrections remain
append-only Reversals in the write model.

## Alternatives considered

- ORM aggregate traversal: rejected because it risks N+1 queries and makes
  union/filter/pagination behavior less explicit.
- A separate CQRS database now: rejected because synchronization, lag, replay,
  and extra operations are not justified at exercise scale.
- Client-side filtering: rejected because it is unbounded and violates the
  server-side pagination requirement.

## Consequences

- Positive: one inspectable query controls filtering, signs, identity, order,
  and pagination.
- Positive: Reporting can evolve independently behind its Interface.
- Negative: SQL is PostgreSQL-aware and must be reconciled with every schema
  change; offset pagination degrades at deep pages.
- Mitigation: integration tests cover filters and stable pages; the proposed
  high-scale projection uses keyset/cursor pagination.

## Revisit triggers

Introduce a separate projection only when measured query load interferes with
write SLOs, replica lag is acceptable for the product, or retention/query needs
cannot be served by partitioned PostgreSQL. Define maximum projection lag,
replay/reconciliation, schema compatibility, and cutover before extraction.
