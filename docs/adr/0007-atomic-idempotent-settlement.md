# Atomic idempotent Settlement

Settlement Preview validates ordered Pricing Quotes without reserving them; Settlement then atomically revalidates, consumes Quotes, transitions Receivables, writes Settlement items, and completes a scoped idempotency record. PostgreSQL constraints and optimistic versions are final safeguards, so retries cannot produce two Settlements or partial financial history.
