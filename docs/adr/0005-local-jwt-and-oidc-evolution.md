# Local JWT with OIDC evolution

The exercise uses locally persisted users and short-lived signed JWTs to demonstrate authentication and role authorization without requiring external infrastructure. Production evolution replaces this Adapter with organizational OIDC; callers depend on Current Actor rather than JWT parsing.
