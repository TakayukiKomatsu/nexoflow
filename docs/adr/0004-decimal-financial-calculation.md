# ADR 0004: Decimal financial calculation

## Status

Accepted — 2026-07-18.

## Context

Pricing compounds a monthly Base Rate and Risk Spread over fractional months,
then may convert currency. Binary floating point cannot exactly represent common
decimal values, and an implicit rounding point would make quote reproduction and
Settlement audit unreliable.

## Decision

Use Java `BigDecimal` for authoritative calculations, PostgreSQL
`NUMERIC(19,4)` for stored money, and `NUMERIC(19,10)` for rates/intermediates.
Rates are decimal fractions. Keep intermediate precision explicit and apply
`HALF_EVEN` only at currency boundaries. API and browser values cross the wire as
decimal strings; JavaScript never produces an authoritative financial result.

## Alternatives considered

- `double`/JavaScript `number`: rejected because representation and exponent
  rounding drift is difficult to audit.
- Scaled integer minor units everywhere: suitable for addition but awkward for
  ten-decimal rates, fractional exponents, and multi-currency scale rules.
- Database-only calculation: rejected because it makes domain strategies and
  independent unit vectors harder to express and port.

## Consequences

- Positive: deterministic vectors can reproduce the stored Pricing Quote across
  JVMs and database round trips.
- Negative: scale, precision, exponent convergence, and rounding must be named
  and tested; `BigDecimal` code is more verbose and slower than binary floats.
- Mitigation: bounded rate/amount validation, exact-vector tests, and snapshot
  persistence make failures observable rather than silently rounded.

## Revisit triggers

Revisit precision only for a new currency scale, instrument class, regulatory
rounding rule, or independently verified calculation standard. A change requires
versioned Strategy semantics, backward reproduction of old quotes, migration
impact analysis, and golden vectors from an independent source.
