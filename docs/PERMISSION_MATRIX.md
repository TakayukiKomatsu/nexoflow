# Permission matrix

Transcribed directly from the authorization filter chain in [`backend/src/main/java/com/srm/creditengine/identity/api/SecurityConfiguration.java`](../backend/src/main/java/com/srm/creditengine/identity/api/SecurityConfiguration.java). Matchers are evaluated in declaration order; the first match wins. Roles come from [`ActorRole`](../backend/src/main/java/com/srm/creditengine/identity/application/ActorRole.java): `OPERATOR`, `ANALYST`, `ADMIN`, `AUDITOR` — a closed enum, so an unrecognized database role value never grants access.

## Endpoint × role matrix

| Method | Endpoint | OPERATOR | ANALYST | ADMIN | AUDITOR | Any authenticated actor | Public |
| --- | --- | :---: | :---: | :---: | :---: | :---: | :---: |
| * | `/api/v1/auth/login` | | | | | | ✅ |
| * | `/api/v1/runtime/**` | | | | | | ✅ |
| * | `/actuator/health/**` | | | | | | ✅ |
| GET | `/actuator/prometheus` | | | ✅ | | | |
| * | `/v3/api-docs/**` | | | | | | ✅ |
| POST | `/api/v1/exchange-rates` | | | ✅ | | | |
| POST | `/api/v1/base-rates` | | | ✅ | | | |
| POST | `/api/v1/product-spreads` | | | ✅ | | | |
| POST | `/api/v1/fx-sync` | | | ✅ | | | |
| GET | `/api/v1/exchange-rates` | | | ✅ | | | |
| GET | `/api/v1/base-rates` | | | ✅ | | | |
| GET | `/api/v1/product-spreads` | | | ✅ | | | |
| GET | `/api/v1/conversions` | ✅ | | ✅ | | | |
| POST | `/api/v1/assignors` | ✅ | | ✅ | | | |
| POST | `/api/v1/receivables` | ✅ | | ✅ | | | |
| POST | `/api/v1/pricing-simulations` | ✅ | | ✅ | | | |
| POST | `/api/v1/pricing-quotes` | ✅ | | ✅ | | | |
| POST | `/api/v1/settlement-previews` | ✅ | | ✅ | | | |
| POST | `/api/v1/settlements` | ✅ | | ✅ | | | |
| POST | `/api/v1/settlements/*/reversals` | | | ✅ | | | |
| GET | `/api/v1/settlement-statements` | ✅ | ✅ | ✅ | ✅ | | |
| GET | `/api/v1/audit-events` | | | ✅ | ✅ | | |
| GET | `/api/v1/assignors/**` | ✅ | ✅ | ✅ | ✅ | | |
| GET | `/api/v1/receivables/**` | ✅ | ✅ | ✅ | ✅ | | |
| GET | `/api/v1/pricing-quotes/**` | ✅ | ✅ | ✅ | ✅ | | |
| GET | `/api/v1/settlements/**` | ✅ | ✅ | ✅ | ✅ | | |
| GET | `/api/v1/users/me` | | | | | ✅ | |
| * | any other request | | | | | | denied — `denyAll()` |

`*` in the Method column means the matcher in `SecurityConfiguration.java` does not restrict by `HttpMethod` — it applies to every HTTP method on that path.

## Role summary

| Role | Capabilities implied by the matrix above |
| --- | --- |
| **OPERATOR** | Runs the core workflow: read conversions; create assignors/receivables; run pricing simulations; create pricing quotes; create settlement previews and settlements; read settlement statements, assignors, receivables, quotes, and settlements. Cannot manage reference data (rates/spreads/FX-sync) and cannot reverse a settlement. |
| **ANALYST** | Read-only: settlement statements, assignors, receivables, pricing quotes, settlements. No write access anywhere, no audit-event access. |
| **ADMIN** | Full reference-data management (create/read exchange rates, base rates, product spreads, trigger FX sync) plus every OPERATOR capability, the exclusive ability to reverse a settlement (`POST /api/v1/settlements/*/reversals`), audit-event read access, and Prometheus metrics read access. The only role with write access to reference data and reversals. |
| **AUDITOR** | Read-only oversight role: settlement statements, audit events, assignors, receivables, pricing quotes, settlements. Cannot write anything and cannot manage reference data. Distinguished from ANALYST by audit-event access. |

## Notes on the security posture

- Roles map to JWT `roles` claims via `JwtGrantedAuthoritiesConverter` with `ROLE_` prefix (`jwtAuthenticationConverter()`), so a JWT must carry `roles: ["OPERATOR"]` (etc.) to satisfy `hasRole("OPERATOR")`/`hasAnyRole(...)`.
- `anyRequest().denyAll()` is the final matcher: any endpoint not explicitly listed above is denied to every actor, authenticated or not. New endpoints must add an explicit matcher or they are unreachable.
- `/api/v1/runtime/**` is permitted to all callers at the Spring Security layer, but `RuntimeValidationController` (`backend/src/main/java/com/srm/creditengine/shared/api/RuntimeValidationController.java`) is itself annotated `@Profile("test")`, so it is not present outside the `test` Spring profile regardless of the matcher.
- Health probes remain public for orchestration. `/actuator/prometheus` is separate and ADMIN-only; authenticated non-ADMIN callers receive `403`, and anonymous callers receive `401`.
- Unauthenticated or malformed-credential requests receive `401 AUTHENTICATION_REQUIRED`; authenticated requests lacking the required role receive `403 ACCESS_DENIED` (both via `SecurityProblemWriter`, formatted as the RFC 9457 Problem Details described in `docs/RUNBOOK.md`).
- Session management is stateless (`SessionCreationPolicy.STATELESS`) and CSRF protection is disabled, consistent with a pure bearer-JWT API with no server-side session or cookie-based auth.
