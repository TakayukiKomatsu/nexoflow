# Requirement traceability

Source: [`README_case_dev_srm.md`](./README_case_dev_srm.md). This matrix is updated as SDD evidence is implemented.

| Source requirement | Planned SDD evidence | Current status |
| --- | --- | --- |
| Java backend and modern SPA | SDD 01 workspace; SDD 03–08 capabilities | Backend and frontend scaffolds implemented; product UI pending SDD 07–08 |
| Currency rates and mocked integration | SDD 03 Currency Module | Reference schema and pure direct/inverse/identity conversion implemented; APIs, effective selection, staleness, and provider resilience pending |
| Strategy pricing and cross-currency conversion | SDD 04 Pricing Module | Money/rate primitives and pure FX conversion implemented; strategy pricing and quotes pending |
| Relational ACID Settlement and race protection | SDD 05 Settlement Module | Planned |
| REST/OpenAPI and controlled errors | SDD 02 runtime contracts | OpenAPI, liveness, correlation, and core Problem Details implemented; PostgreSQL/Compose/readiness matrix pending |
| Filtered, optimized settlement statement | SDD 06 Reporting Module | Planned |
| Operator simulation and paginated grid | SDD 07–08 frontend Modules | Planned |
| Docker/Compose | SDD 02 runtime | Deferred: Docker unavailable by user direction |
| Validation, exception handling, pricing tests | SDD 02–04 | Validation/error contracts and money/rate/FX unit tests implemented; pricing strategies pending |
| Hooks, CI, C4, observability, resilience, locking | SDD 01, 05, 09–10 | Hooks, CI, ADRs, and an architecture boundary test implemented; C4, resilience, locking, and observability pending |
| ADRs, high-scale design, EDA, crisis exercise | SDD 01, 11–12 | ADRs implemented; remaining artifacts pending |
| ER diagram and DDL scripts | SDD 01 ER; SDD 02+ Flyway | ER and Flyway migrations V1–V5 implemented; PostgreSQL validation pending |
| AI usage documentation | SDD 11 | Planned |
