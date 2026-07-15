# SRM Credit Engine

The SRM Credit Engine prices and settles credit receivables for assignors in BRL and USD. This glossary is the system's ubiquitous language; it intentionally contains no implementation decisions.

## Parties and assets

**Assignor**:
The legal entity that transfers a receivable to the fund for settlement.
_Avoid_: Cedent, client, customer

**Receivable**:
A registered credit asset with a face value, due date, Product Type, and Asset Currency, owned by one Assignor.
_Avoid_: Title, transaction, payment

**Product Type**:
The classification of a Receivable that determines its risk spread, such as Mercantile Invoice or Post-dated Cheque.
_Avoid_: Product, strategy, receivable kind

**Asset Currency**:
The Supported Currency in which a Receivable's face value is denominated.
_Avoid_: Title currency, source currency

**Settlement Currency**:
The Supported Currency in which the discounted value is paid in a Settlement.
_Avoid_: Payment currency, target currency

**Supported Currency**:
BRL or USD, the only currencies accepted by the MVP.
_Avoid_: Any ISO currency

## Pricing

**Pricing Simulation**:
A server-authoritative, non-persisted calculation of a prospective Receivable value. It is neither an offer nor a reservation.
_Avoid_: Quote, pre-settlement

**Pricing Quote**:
An immutable, auditable record of a Receivable's calculated settlement value that is valid for 15 minutes.
_Avoid_: Simulation, reservation

**Pricing Term**:
The whole calendar-day interval from pricing date to due date, expressed as days divided by 30, including fractional months.
_Avoid_: Business-day term, whole-month term

**Base Rate**:
The effective-dated monthly discount rate selected by Asset Currency before a Product Type's risk spread is applied.
_Avoid_: Interest rate, funding rate

**Risk Spread**:
The effective-dated monthly rate associated with a Product Type and added to the Base Rate for pricing.
_Avoid_: Fee, markup

**Exchange-Rate Pair**:
A `BASE/QUOTE` observation where one unit of the base currency equals the stored quote-currency amount.
_Avoid_: Unqualified FX rate

## Settlement and history

**Settlement Preview**:
A non-persisted server validation of ordered Pricing Quotes that returns authoritative batch totals and may become stale before Settlement.
_Avoid_: Reservation, draft settlement

**Settlement**:
The immutable, all-or-nothing record that consumes valid Pricing Quotes and records payment for their Receivables.
_Avoid_: Transaction, liquidation batch

**Settlement Reversal**:
An immutable correction that compensates an entire Settlement and terminally marks its Receivables as `REVERSED`.
_Avoid_: Undo, cancellation, reopening a receivable

**Settlement Ledger Entry**:
An immutable signed reporting entry produced by a Settlement item or its Settlement Reversal.
_Avoid_: Statement row, transaction history
