import type { PricingQuote } from "../api/client";
import type { SettlementIntentWorkflow } from "../hooks/useSettlementIntent";

export function SettlementIntent({
  quotes,
  workflow,
}: {
  quotes: PricingQuote[];
  workflow: SettlementIntentWorkflow;
}) {
  const {
    canSettle,
    cancel,
    confirm,
    intent,
    intentMatchesSelection,
    message,
    preview,
    previewExpired,
    previewMatchesSelection,
    previewPending,
    quoteIds,
    requestPreview,
    result,
    selected,
    settlementPending,
    toggle,
  } = workflow;

  return (
    <section
      className="card settlement"
      aria-labelledby="settlement-title"
      aria-busy={previewPending || settlementPending}
    >
      <h2 id="settlement-title">Settlement intent</h2>
      {canSettle && quotes.length ? (
        <fieldset>
          <legend>Select active quotes</legend>
          {quotes.map((quote) => (
            <label className="quote-choice" key={quote.id}>
              <input
                type="checkbox"
                checked={selected.includes(quote.id)}
                onChange={(event) => toggle(quote.id, event.target.checked)}
              />
              {quote.id} — {quote.pricing.settlementAmount}{" "}
              {quote.pricing.settlementCurrency}
            </label>
          ))}
        </fieldset>
      ) : canSettle ? (
        <p className="empty">
          Create one or more quotes before requesting settlement.
        </p>
      ) : (
        <p className="empty">
          Your role can view settlements but cannot create them.
        </p>
      )}
      {canSettle && (
        <div className="actions">
          <button
            onClick={requestPreview}
            disabled={previewPending || settlementPending || !quoteIds.length}
          >
            {previewPending
              ? "Requesting server preview…"
              : "Request server preview"}
          </button>
          {intent && !result && (
            <button
              className="secondary"
              onClick={cancel}
              disabled={previewPending || settlementPending}
            >
              Cancel intent
            </button>
          )}
        </div>
      )}
      {intent && !result && (
        <p className="hint">
          {intentMatchesSelection
            ? `A saved settlement intent was restored for ${intent.quoteIds.length} quote(s). Request a fresh server preview before confirming.`
            : "Cancel the saved settlement intent before confirming a different quote selection."}
        </p>
      )}
      {canSettle && preview && previewMatchesSelection && (
        <div className="preview" aria-label="Server settlement preview">
          <p>
            Server preview at {preview.asOf}; expires {preview.earliestExpiry}
          </p>
          <ul>
            {preview.items.map((item) => (
              <li key={item.quoteId}>
                {item.quoteId}: {item.settlementAmount}{" "}
                {preview.settlementCurrency}
              </li>
            ))}
          </ul>
          <p className="amount">
            <span>Server total</span>
            <output>{preview.totalAmount}</output>
            <span>{preview.settlementCurrency}</span>
          </p>
          {previewExpired && (
            <p className="error" role="alert">
              This preview has expired. Request a fresh server preview.
            </p>
          )}
          <button
            onClick={confirm}
            disabled={
              previewPending ||
              settlementPending ||
              !previewMatchesSelection ||
              !intentMatchesSelection ||
              previewExpired
            }
          >
            {settlementPending
              ? "Confirming settlement…"
              : "Confirm settlement"}
          </button>
        </div>
      )}
      {result && (
        <p role="status">
          Settlement ID{" "}
          <a href={`#settlement-${result.settlementId}`}>
            {result.settlementId}
          </a>{" "}
          confirmed.
        </p>
      )}
      {message && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
    </section>
  );
}
