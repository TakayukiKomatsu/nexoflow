# ADR 0001: Modular monolith

## Status

Accepted — 2026-07-18.

## Context

Pricing, quote consumption, Settlement, Reversal, and audit history share strong
transactional invariants. The exercise has a 3–4 day delivery window and one
small team, while the staff-level design must leave credible extraction seams.
Starting with independently deployed services would add network failure,
distributed tracing, schema ownership, and cross-service consistency work before
measured load or team boundaries justify it.

## Decision

Deliver one Spring Boot deployable organized by business capability. Each Module
owns its API, application orchestration, domain policy, and infrastructure
Adapters. Cross-Module access goes through explicit Interfaces; PostgreSQL is a
shared physical store for now, but table ownership remains attributable. The
React SPA and database are separate runtime containers.

## Alternatives considered

- Microservices from day one: rejected because Settlement would immediately
  require a saga or distributed transaction and a larger operational platform.
- A package-by-technical-layer monolith: rejected because ownership and future
  extraction boundaries would be obscured.
- Serverless functions per endpoint: rejected because cold starts, connection
  management, and fragmented transaction ownership do not fit this write path.

## Consequences

- Positive: atomic financial changes stay in one database transaction; local
  development, CI, and incident diagnosis remain simple.
- Positive: capability packages and Interfaces provide incremental extraction
  points without paying distributed-system cost today.
- Negative: one deployment couples release cadence and has a single horizontal
  scaling unit; the shared database can permit accidental cross-Module coupling.
- Mitigation: architecture tests, ownership documentation, and the proposed
  partitioned evolution keep dependencies visible.

## Revisit triggers

Revisit when a capability needs an independent SLO or release cadence, a named
team can own its data and on-call rotation, or measurements show one capability
consistently consumes more than 70% of shared CPU/database capacity. Extraction
still requires an outbox, contract/versioning plan, failure budget, and a proven
way to preserve Settlement consistency; headcount or diagram aesthetics alone
are not triggers.
