# ADR 0007: Atomic, idempotent Settlement

## Status

Accepted — 2026-07-18.

## Context

An operator can retry after a timeout, two requests can race for the same
Receivable, and a batch can fail after some SQL statements. The externally
observable contract must never create two Settlements, consume half a batch, or
bind one idempotency key to two payloads.

## Decision

Require `Idempotency-Key` for Settlement and Reversal. Scope a record by actor,
operation, and key; store a canonical request hash and final result. In one
PostgreSQL transaction, claim the key, revalidate ordered Quotes/Receivables,
apply optimistic transitions, insert Settlement/items/audit rows, and complete
the idempotency record. Same key/hash returns the original response; a different
hash returns `409`. Database uniqueness and versions are final race safeguards.
Do not automatically retry Settlement transactions.

The database rejects deletion of `PROCESSING` records and every identity/status
mutation outside the one `PROCESSING` → `COMPLETED` transition. A `COMPLETED`
record remains update-immutable but may be deleted by an explicit retention
process; deleting it deliberately ends replay for that expired key.

## Alternatives considered

- Client-only retry suppression: rejected because clients crash and requests
  race independently.
- Pessimistic locks for the whole workflow: rejected as the default because lock
  duration/contention is less predictable; uniqueness and optimistic versions
  provide a smaller critical section.
- Distributed saga now: rejected because all authoritative rows share one
  database and compensation would weaken an invariant that can be atomic.

## Consequences

- Positive: network retries are safe and concurrent attempts have one winner;
  fault injection can prove complete rollback.
- Negative: keys need retention/abuse limits, canonical hashing is a compatibility
  contract, and long transactions can still contend.
- Mitigation: bounded requests, unique constraints, deterministic lock/update
  order, metrics, and reconciliation surface anomalies.

## Revisit triggers

If Settlement crosses databases/services, replace this local transaction only
with a designed command log/outbox and idempotent saga whose compensation and
reconciliation are explicit. Revisit key retention when measured volume, legal
retention, or abuse changes; preserve replay semantics throughout migration.
