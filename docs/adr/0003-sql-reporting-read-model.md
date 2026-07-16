# SQL reporting read model

Reporting uses an optimized SQL read Adapter instead of reconstructing write aggregates because Settlement Ledger Entry queries need composable filters, predictable pagination, and index-aware execution. This deliberate read path remains behind Reporting's small ledger-query Interface.
