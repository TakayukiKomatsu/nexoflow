# Requirement traceability

Source: [`README_case_dev_srm.md`](./README_case_dev_srm.md). This matrix is updated as SDD evidence is implemented.

| Source requirement | Planned SDD evidence | Current status |
| --- | --- | --- |
| Java backend and modern SPA | SDD 01 workspace; SDD 03–08 capabilities | Foundation scaffolded |
| Currency rates and mocked integration | SDD 03 Currency Module | Planned |
| Strategy pricing and cross-currency conversion | SDD 04 Pricing Module | Planned |
| Relational ACID Settlement and race protection | SDD 05 Settlement Module | Planned |
| REST/OpenAPI and controlled errors | SDD 02 runtime contracts | Planned |
| Filtered, optimized settlement statement | SDD 06 Reporting Module | Planned |
| Operator simulation and paginated grid | SDD 07–08 frontend Modules | Planned |
| Docker/Compose | SDD 02 runtime | Deferred: Docker unavailable by user direction |
| Validation, exception handling, pricing tests | SDD 02–04 | Planned |
| Hooks, CI, C4, observability, resilience, locking | SDD 01, 05, 09–10 | In progress |
| ADRs, high-scale design, EDA, crisis exercise | SDD 01, 11–12 | ADRs scaffolded |
| ER diagram and DDL scripts | SDD 01 ER; SDD 02+ Flyway | ER scaffolded; DDL planned |
| AI usage documentation | SDD 11 | Planned |
