# ADR 0006: Immutable Pricing Quotes

## Status

Accepted — 2026-07-18.

## Context

Base Rates, Risk Spreads, Receivable state, and Exchange Rates can change between
simulation and Settlement. Recalculating later would make the amount shown to an
operator differ from the financial decision consumed by Settlement and would
erase the inputs needed for audit.

## Decision

Persist a Pricing Quote as a complete 15-minute snapshot: Receivable and product
identity, face/settlement currencies, amounts, Base Rate, Risk Spread, Strategy,
day-count term, FX observation, calculated outputs, actor, and timestamps. The
only mutation is `ACTIVE` to `CONSUMED`, with PostgreSQL guards proving every
snapshot field remains unchanged. Expiry or changed Receivable state requires a
new quote.

## Alternatives considered

- Recalculate at Settlement: rejected because reference changes break operator
  intent and reproducibility.
- Mutable quote row: rejected because corrections could rewrite financial
  history; a new quote is clearer.
- Store only input IDs: rejected because referenced rate/config rows may evolve
  or be unavailable during audit.

## Consequences

- Positive: Settlement uses exactly the reviewed outcome and remains
  reproducible after reference-data changes.
- Negative: snapshots duplicate data and require migration/version discipline;
  expiration creates expected user retries.
- Mitigation: immutable columns, explicit lifecycle, bounded TTL, and complete
  contract tests protect the extra storage.

## Revisit triggers

Revisit the TTL or snapshot envelope when market volatility, regulation, product
terms, or signing/non-repudiation requirements change. Preserve old Strategy
versions and historical readability; never migrate by silently recalculating an
accepted quote.
