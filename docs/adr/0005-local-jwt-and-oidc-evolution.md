# ADR 0005: Local JWT with OIDC evolution

## Status

Accepted for local exercise use — 2026-07-18. Not approved as a production
identity architecture.

## Context

The case requires authenticated roles but provides no identity tenant,
credentials, or external availability contract. Reviewers need a deterministic
local login while production would require centralized lifecycle, MFA, key
rotation, revocation, and organizational policy.

## Decision

Persist local BCrypt users and issue 15-minute HMAC JWT access tokens only for
the exercise. Seed reviewer accounts solely through the opt-in `dev` profile and
environment variables. Authorization depends on the Current Actor Interface, so
an OIDC resource-server Adapter can replace local token issuance without
changing financial use cases. Refresh tokens are intentionally absent.

## Alternatives considered

- External OIDC now: rejected because no authorized provider/tenant exists and
  it would make offline review nondeterministic.
- Server-side sessions: viable for one deployment, but less representative of
  the intended API boundary and still lacks enterprise identity lifecycle.
- Long-lived static API keys: rejected due to weak user attribution and rotation.

## Consequences

- Positive: role checks and audit actors are executable in local/CI environments.
- Negative: local HMAC key custody, browser token storage, login throttling, and
  lack of revocation/MFA are deliberate gaps, not production claims.
- Mitigation: short expiry, environment-provided secret, BCrypt, deny-by-default
  routes, redaction tests, and explicit documentation bound the exercise risk.

## Revisit triggers

OIDC is mandatory before production, federation, multiple API deployments, or
real user onboarding. The replacement must define issuer/audience validation,
JWKS rotation/cache failure, MFA and account lifecycle, role/group mapping,
revocation/session policy, clock skew, audit subject stability, and outage mode.
