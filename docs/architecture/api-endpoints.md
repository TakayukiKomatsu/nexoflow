# API endpoint inventory

This inventory is checked against every `/api/v1` operation in the freshly
exported runtime OpenAPI document by `scripts/validate-api-docs.mjs`. The
generated document remains the executable wire-contract authority; this page is
a reviewer index, not a second handwritten schema.

| Capability | Method and path |
| --- | --- |
| Identity | `POST /api/v1/auth/login` |
| Identity | `GET /api/v1/users/me` |
| Assignor | `POST /api/v1/assignors` |
| Assignor | `GET /api/v1/assignors` |
| Assignor | `GET /api/v1/assignors/{id}` |
| Receivable | `POST /api/v1/receivables` |
| Receivable | `GET /api/v1/receivables` |
| Receivable | `GET /api/v1/receivables/{id}` |
| Exchange rate | `POST /api/v1/exchange-rates` |
| Exchange rate | `GET /api/v1/exchange-rates` |
| Reference rate | `POST /api/v1/base-rates` |
| Reference rate | `GET /api/v1/base-rates` |
| Reference rate | `POST /api/v1/product-spreads` |
| Reference rate | `GET /api/v1/product-spreads` |
| FX provider | `POST /api/v1/fx-sync` |
| Conversion | `GET /api/v1/conversions` |
| Pricing | `POST /api/v1/pricing-simulations` |
| Pricing | `POST /api/v1/pricing-quotes` |
| Pricing | `GET /api/v1/pricing-quotes/{id}` |
| Settlement | `POST /api/v1/settlement-previews` |
| Settlement | `POST /api/v1/settlements` |
| Settlement | `GET /api/v1/settlements/{settlementId}` |
| Reversal | `POST /api/v1/settlements/{settlementId}/reversals` |
| Reporting | `GET /api/v1/settlement-statements` |
| Audit | `GET /api/v1/audit-events` |
| Runtime test contract | `GET /api/v1/runtime/validation` |
| Runtime test contract | `GET /api/v1/runtime/failure` |
| Runtime test contract | `POST /api/v1/runtime/echo` |

OpenAPI is served at `/v3/api-docs`; liveness, readiness, and authenticated
Prometheus endpoints live under `/actuator` and are operational endpoints rather
than `/api/v1` business endpoints.
