import type { Session } from "../api/client";
import {
  toLocalDateTimeValue,
  toUtcInstant,
  useStatementFilters,
} from "../hooks/useStatementFilters";

export function StatementLedger({
  session,
  refreshRevision,
}: {
  session: Session;
  refreshRevision: number;
}) {
  const { change, clear, filters, loading, message, move, page, retry } =
    useStatementFilters(session, refreshRevision);

  return (
    <section
      className="card ledger"
      aria-labelledby="statement-title"
      aria-busy={loading}
    >
      <h2 id="statement-title">Signed settlement statement</h2>
      <div className="ledger-filters">
        <label>
          Ledger settlement currency
          <input
            aria-label="Ledger settlement currency"
            value={filters.get("settlementCurrency") ?? ""}
            onChange={(event) =>
              change("settlementCurrency", event.target.value.toUpperCase())
            }
            maxLength={3}
          />
        </label>
        <label>
          Ledger product
          <input
            aria-label="Ledger product"
            value={filters.get("productType") ?? ""}
            onChange={(event) => change("productType", event.target.value)}
          />
        </label>
        <label>
          From
          <input
            aria-label="From filter"
            type="datetime-local"
            value={toLocalDateTimeValue(filters.get("from") ?? undefined)}
            onChange={(event) =>
              change("from", toUtcInstant(event.target.value))
            }
          />
        </label>
        <label>
          To
          <input
            aria-label="To filter"
            type="datetime-local"
            value={toLocalDateTimeValue(filters.get("to") ?? undefined)}
            onChange={(event) => change("to", toUtcInstant(event.target.value))}
          />
        </label>
        <label>
          Ledger assignor ID
          <input
            aria-label="Ledger assignor ID"
            value={filters.get("assignorId") ?? ""}
            onChange={(event) => change("assignorId", event.target.value)}
          />
        </label>
        <label>
          Ledger asset currency
          <input
            aria-label="Ledger asset currency"
            value={filters.get("assetCurrency") ?? ""}
            onChange={(event) =>
              change("assetCurrency", event.target.value.toUpperCase())
            }
            maxLength={3}
          />
        </label>
        <label>
          Rows
          <select
            aria-label="Page size"
            value={filters.get("size") ?? "50"}
            onChange={(event) => change("size", event.target.value)}
          >
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
          </select>
        </label>
      </div>
      {loading && <p role="status">Loading settlement statement…</p>}
      {message && (
        <div>
          <p className="error" role="alert">
            {message}
          </p>
          <div className="actions">
            <button className="secondary" onClick={retry}>
              Retry statement request
            </button>
          </div>
        </div>
      )}
      {page?.entries.length === 0 && !message && (
        <div>
          <p className="empty" role="status">
            No settlement entries match these filters.
          </p>
          <div className="actions">
            <button className="secondary" onClick={clear}>
              Clear ledger filters
            </button>
          </div>
        </div>
      )}
      {page && page.entries.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Signed amount</th>
              <th>Currency</th>
              <th>Settlement</th>
              <th>Effective at</th>
            </tr>
          </thead>
          <tbody>
            {page.entries.map((entry) => (
              <tr key={entry.entryId} className={entry.entryType.toLowerCase()}>
                <td>{entry.entryType}</td>
                <td>{entry.signedAmount}</td>
                <td>{entry.settlementCurrency}</td>
                <td>
                  <a href={`#settlement-${entry.settlementId}`}>
                    {entry.settlementId}
                  </a>
                </td>
                <td>{entry.effectiveAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {page && (
        <div className="pager">
          <button
            className="secondary"
            onClick={() => move(page.page - 1)}
            disabled={page.page === 0}
          >
            Previous
          </button>
          <span>Page {page.page + 1}</span>
          <button
            className="secondary"
            onClick={() => move(page.page + 1)}
            disabled={!page.hasNext}
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}
