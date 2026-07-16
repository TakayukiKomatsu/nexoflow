# Decimal financial calculation

Authoritative money and rates use Java `BigDecimal`, PostgreSQL `NUMERIC`, and `HALF_EVEN` at currency boundaries; browser values remain decimal strings. This avoids binary-floating-point drift in Pricing Quote and Settlement records, at the cost of making precision and rounding explicit domain rules.
