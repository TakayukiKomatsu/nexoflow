# PostgreSQL for the write model

PostgreSQL is the authoritative write store because Settlement requires relational constraints, ACID transactions, and concurrency control that are costly and surprising to reconstruct across eventually consistent stores. Flyway owns schema evolution; write Modules hide persistence details behind their Interfaces.
