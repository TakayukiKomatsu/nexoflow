# Fixture schema inventory

## Final table columns

Storage tokens encode exact database types; `PK`, `UK`, and `FK` mark inline
constraints.

```text
    parents {
        uuid id PK
        varchar_20 code UK
    }
    children {
        uuid id PK
        uuid parent_id FK
        uuid java_parent_id FK
        uuid table_parent_id FK
        int amount
        decimal_19_4 money
        varchar_20 code UK
        char_2 fixed_code
        text notes
        timestamptz occurred_at
    }
```

Migrations:

- `scripts/tests/fixtures/schema-docs/sql-valid/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-inline-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-type-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-nullability-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-alter-type-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-alter-nullability-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-drop-constraint-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-varchar-width-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/sql-timestamp-type-mutated/V1__fixture.sql`
- `scripts/tests/fixtures/schema-docs/java-valid/V2__fixture.java`
- `scripts/tests/fixtures/schema-docs/java-mutated/V2__fixture.java`

Named constraints: `children_parent_fk`, `children_table_parent_fk`, `children_amount_check`,
`children_code_unique`, `children_java_parent_fk`.

Structural constraints: `parents.primary(id)`, `parents.unique(code)`,
`children.primary(id)`, `children.fk(table_parent_id->parents.id)`,
`children.fk(parent_id->parents.id)`, `children.check(amount>0)`,
`children.unique(code)`, and
`children.fk(java_parent_id->parents.id)`.

Financial columns: `children.numeric(money:numeric(19,4):not-null)`.
