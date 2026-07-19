import { useEffect, useRef, useState } from "react";
import {
  ApiError,
  api,
  type PricingQuote,
  type Session,
  type Settlement,
  type SettlementPreview,
  type StatementPage,
} from "./api/client";

type Intent = { email: string; quoteIds: string[]; key: string };
const INTENT_STORAGE_KEY = "srm-settlement-intent";
type PreviewState = { quoteKey: string; value: SettlementPreview };
export const SETTLEMENT_TIMEOUT_MS = 10_000;

function loadIntent(email: string): Intent | undefined {
  try {
    const value = localStorage.getItem(INTENT_STORAGE_KEY);
    if (!value) return undefined;
    const intent = JSON.parse(value) as Partial<Intent>;
    if (
      intent.email !== email ||
      !Array.isArray(intent.quoteIds) ||
      !intent.quoteIds.every((id) => typeof id === "string") ||
      typeof intent.key !== "string"
    )
      return undefined;
    return { email, quoteIds: intent.quoteIds, key: intent.key };
  } catch {
    return undefined;
  }
}
function makeKey() {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `settlement-${Date.now()}-${Math.random()}`
  );
}
function errorMessage(cause: unknown, fallback: string) {
  if (!(cause instanceof ApiError)) return fallback;
  if (cause.status === 401) return "Your session has expired. Sign in again.";
  if (cause.status === 403)
    return "Your role is not allowed to perform this action.";
  return cause.message;
}

export function SettlementWorkspace({
  session,
  quotes,
  onExpired,
  showLedger = true,
}: {
  session: Session;
  quotes: PricingQuote[];
  onExpired: () => void;
  showLedger?: boolean;
}) {
  const [selected, setSelected] = useState<string[]>([]);
  const [previewState, setPreviewState] = useState<PreviewState>();
  const [previewExpired, setPreviewExpired] = useState(false);
  const [intent, setIntent] = useState<Intent | undefined>(() =>
    loadIntent(session.email),
  );
  const [result, setResult] = useState<Settlement>();
  const [message, setMessage] = useState<string>();
  const [previewPending, setPreviewPending] = useState(false);
  const [settlementPending, setSettlementPending] = useState(false);
  const previewRequestId = useRef(0);
  const previewAbort = useRef<AbortController | undefined>(undefined);
  const quoteIds = selected;
  const quoteKey = quoteIds.join(",");
  const currentQuoteKey = useRef(quoteKey);
  currentQuoteKey.current = quoteKey;
  const preview = previewState?.value;
  const previewMatchesSelection = previewState?.quoteKey === quoteKey;
  const intentMatchesSelection =
    !intent || intent.quoteIds.join(",") === quoteKey;
  const canSettle =
    session.roles.includes("OPERATOR") || session.roles.includes("ADMIN");

  useEffect(() => {
    const recovered = loadIntent(session.email);
    setIntent(recovered);
    // Keep IDs even when the quote list has not been reloaded yet. The server
    // remains authoritative when a recovered intent requests a fresh preview.
    setSelected(recovered?.quoteIds ?? []);
  }, [session.email]);

  useEffect(() => {
    previewRequestId.current += 1;
    previewAbort.current?.abort();
    setPreviewPending(false);
    setPreviewState(undefined);
    setPreviewExpired(false);
    setResult(undefined);
    setMessage(undefined);
  }, [quoteKey]);

  useEffect(() => {
    if (!preview) {
      setPreviewExpired(false);
      return;
    }
    const expiresAt = Date.parse(preview.earliestExpiry);
    let timeout: number | undefined;
    const scheduleExpiry = () => {
      const remaining = expiresAt - Date.now();
      if (remaining <= 0) {
        setPreviewExpired(true);
        return;
      }
      timeout = window.setTimeout(
        scheduleExpiry,
        Math.min(remaining, 2_147_483_647),
      );
    };
    setPreviewExpired(false);
    scheduleExpiry();
    return () => {
      if (timeout !== undefined) window.clearTimeout(timeout);
    };
  }, [preview]);

  useEffect(
    () => () => {
      previewAbort.current?.abort();
    },
    [],
  );

  function toggle(id: string, checked: boolean) {
    setSelected((old) =>
      checked ? [...old, id] : old.filter((value) => value !== id),
    );
  }

  async function requestPreview() {
    if (!canSettle || !quoteIds.length) return;
    previewAbort.current?.abort();
    const controller = new AbortController();
    previewAbort.current = controller;
    const requestId = ++previewRequestId.current;
    const requestedQuoteKey = quoteKey;
    setPreviewPending(true);
    setMessage(undefined);
    try {
      const value = await api.previewSettlement(
        quoteIds,
        session.accessToken,
        controller.signal,
      );
      if (
        requestId !== previewRequestId.current ||
        requestedQuoteKey !== currentQuoteKey.current
      )
        return;
      setPreviewState({ quoteKey: requestedQuoteKey, value });
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === "AbortError") return;
      if (
        requestId !== previewRequestId.current ||
        requestedQuoteKey !== currentQuoteKey.current
      )
        return;
      setPreviewState(undefined);
      const text = errorMessage(
        cause,
        "Could not obtain a settlement preview.",
      );
      if (cause instanceof ApiError && cause.status === 401) onExpired();
      setMessage(text);
    } finally {
      if (requestId === previewRequestId.current) {
        setPreviewPending(false);
        if (previewAbort.current === controller)
          previewAbort.current = undefined;
      }
    }
  }

  function clearIntent() {
    localStorage.removeItem(INTENT_STORAGE_KEY);
    setIntent(undefined);
    setPreviewState(undefined);
  }

  async function confirm() {
    if (!canSettle) return;
    if (!intentMatchesSelection) {
      setMessage(
        "Cancel the saved settlement intent before confirming a different quote selection.",
      );
      return;
    }
    if (
      !preview ||
      !previewMatchesSelection ||
      previewExpired ||
      Date.parse(preview.earliestExpiry) <= Date.now()
    ) {
      setPreviewState(undefined);
      setMessage(
        "This preview is stale or expired. Request a fresh server preview.",
      );
      return;
    }
    const reusingIntent = intent && intent.quoteIds.join(",") === quoteKey;
    const nextIntent = reusingIntent
      ? intent
      : { email: session.email, quoteIds: [...quoteIds], key: makeKey() };
    localStorage.setItem(INTENT_STORAGE_KEY, JSON.stringify(nextIntent));
    setIntent(nextIntent);
    setSettlementPending(true);
    setMessage(undefined);
    const controller = new AbortController();
    let timedOut = false;
    const timeout = window.setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, SETTLEMENT_TIMEOUT_MS);
    try {
      const settlement = await api.settle(
        nextIntent.quoteIds,
        nextIntent.key,
        session.accessToken,
        controller.signal,
      );
      setResult(settlement);
      clearIntent();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onExpired();
      if (cause instanceof ApiError) {
        switch (cause.code) {
          case "IDEMPOTENCY_KEY_REUSED":
            clearIntent();
            setMessage(
              "This key belongs to a different request. Request a fresh preview.",
            );
            return;
          case "ALREADY_SETTLED":
            clearIntent();
            setMessage(
              "A selected receivable is already settled. Review the ledger.",
            );
            return;
          case "PRICING_QUOTE_EXPIRED":
            clearIntent();
            setMessage(
              "A pricing quote expired. Create a fresh quote and preview.",
            );
            return;
        }
      }
      const fallback = timedOut
        ? "The settlement request timed out and the outcome is unknown."
        : "The outcome is unknown.";
      setMessage(
        `${errorMessage(cause, fallback)} Retry uses the same settlement intent.`,
      );
    } finally {
      window.clearTimeout(timeout);
      setSettlementPending(false);
    }
  }

  function cancel() {
    clearIntent();
    setMessage("Settlement intent cancelled.");
  }

  return (
    <>
      <section className="card settlement" aria-labelledby="settlement-title">
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
      <SettlementDetail session={session} onExpired={onExpired} />
      {showLedger && (
        <StatementLedger session={session} onExpired={onExpired} />
      )}
    </>
  );
}

function settlementFromHash() {
  const match = window.location.hash.match(/^#settlement-([^/]+)$/);
  return match?.[1];
}

function SettlementDetail({
  session,
  onExpired,
}: {
  session: Session;
  onExpired: () => void;
}) {
  const [settlementId, setSettlementId] = useState(settlementFromHash);
  const [settlement, setSettlement] = useState<Settlement>();
  const [message, setMessage] = useState<string>();
  const detailRequestId = useRef(0);
  useEffect(() => {
    const update = () => setSettlementId(settlementFromHash());
    window.addEventListener("hashchange", update);
    return () => window.removeEventListener("hashchange", update);
  }, []);
  useEffect(() => {
    const requestId = ++detailRequestId.current;
    if (!settlementId) {
      setSettlement(undefined);
      setMessage(undefined);
      return;
    }
    const requestKey = settlementId;
    const controller = new AbortController();
    setSettlement(undefined);
    setMessage(undefined);
    api
      .settlement(requestKey, session.accessToken, controller.signal)
      .then((nextSettlement) => {
        if (
          controller.signal.aborted ||
          requestId !== detailRequestId.current ||
          settlementFromHash() !== requestKey
        )
          return;
        setSettlement(nextSettlement);
      })
      .catch((cause) => {
        if (
          controller.signal.aborted ||
          requestId !== detailRequestId.current ||
          settlementFromHash() !== requestKey
        )
          return;
        setSettlement(undefined);
        if (cause instanceof ApiError && cause.status === 401) onExpired();
        setMessage(
          errorMessage(cause, "Could not load the settlement detail."),
        );
      });
    return () => controller.abort();
  }, [settlementId, session.accessToken, onExpired]);
  if (!settlementId) return null;
  return (
    <section
      className="card settlement-detail"
      aria-labelledby="settlement-detail-title"
    >
      <h2 id="settlement-detail-title">Settlement detail</h2>
      {settlement ? (
        <>
          <p>
            <strong>{settlement.settlementId}</strong> — {settlement.status}
          </p>
          <p className="amount">
            <span>Total</span>
            <output>{settlement.totalAmount}</output>
            <span>{settlement.settlementCurrency}</span>
          </p>
          <ul>
            {settlement.items.map((item) => (
              <li key={item.quoteId}>
                {item.quoteId}: {item.settlementAmount}
              </li>
            ))}
          </ul>
        </>
      ) : message ? (
        <p className="error" role="alert">
          {message}
        </p>
      ) : (
        <p>Loading settlement…</p>
      )}
    </section>
  );
}

function StatementLedger({
  session,
  onExpired,
}: {
  session: Session;
  onExpired: () => void;
}) {
  const [search, setSearch] = useState(
    () => new URLSearchParams(window.location.search),
  );
  const [page, setPage] = useState<StatementPage>();
  const [message, setMessage] = useState<string>();
  const statementRequestId = useRef(0);
  useEffect(() => {
    const requestId = ++statementRequestId.current;
    const requestKey = search.toString();
    const controller = new AbortController();
    setPage(undefined);
    setMessage(undefined);
    api
      .statement(search, session.accessToken, controller.signal)
      .then((nextPage) => {
        if (
          controller.signal.aborted ||
          requestId !== statementRequestId.current ||
          new URLSearchParams(window.location.search).toString() !== requestKey
        )
          return;
        setPage(nextPage);
      })
      .catch((cause) => {
        if (
          controller.signal.aborted ||
          requestId !== statementRequestId.current ||
          new URLSearchParams(window.location.search).toString() !== requestKey
        )
          return;
        if (cause instanceof ApiError && cause.status === 401) onExpired();
        setMessage(
          errorMessage(cause, "Could not load the settlement statement."),
        );
      });
    return () => controller.abort();
  }, [search, session.accessToken, onExpired]);
  useEffect(() => {
    const restore = () =>
      setSearch(new URLSearchParams(window.location.search));
    window.addEventListener("popstate", restore);
    return () => window.removeEventListener("popstate", restore);
  }, []);
  function navigate(next: URLSearchParams) {
    window.history.pushState(
      null,
      "",
      `${window.location.pathname}${next.toString() ? `?${next}` : ""}${window.location.hash}`,
    );
    setSearch(next);
  }
  function change(name: string, value: string) {
    const next = new URLSearchParams(search);
    if (value) next.set(name, value);
    else next.delete(name);
    next.set("page", "0");
    navigate(next);
  }
  function move(nextPage: number) {
    const next = new URLSearchParams(search);
    next.set("page", String(nextPage));
    navigate(next);
  }
  return (
    <section className="card ledger" aria-labelledby="statement-title">
      <h2 id="statement-title">Signed settlement statement</h2>
      <div className="ledger-filters">
        <label>
          Ledger settlement currency
          <input
            aria-label="Ledger settlement currency"
            value={search.get("settlementCurrency") ?? ""}
            onChange={(e) =>
              change("settlementCurrency", e.target.value.toUpperCase())
            }
            maxLength={3}
          />
        </label>
        <label>
          Ledger product
          <input
            aria-label="Ledger product"
            value={search.get("productType") ?? ""}
            onChange={(e) => change("productType", e.target.value)}
          />
        </label>
        <label>
          From
          <input
            aria-label="From filter"
            type="datetime-local"
            value={search.get("from")?.slice(0, 16) ?? ""}
            onChange={(e) =>
              change(
                "from",
                e.target.value ? new Date(e.target.value).toISOString() : "",
              )
            }
          />
        </label>
        <label>
          To
          <input
            aria-label="To filter"
            type="datetime-local"
            value={search.get("to")?.slice(0, 16) ?? ""}
            onChange={(e) =>
              change(
                "to",
                e.target.value ? new Date(e.target.value).toISOString() : "",
              )
            }
          />
        </label>
        <label>
          Ledger assignor ID
          <input
            aria-label="Ledger assignor ID"
            value={search.get("assignorId") ?? ""}
            onChange={(e) => change("assignorId", e.target.value)}
          />
        </label>
        <label>
          Ledger asset currency
          <input
            aria-label="Ledger asset currency"
            value={search.get("assetCurrency") ?? ""}
            onChange={(e) =>
              change("assetCurrency", e.target.value.toUpperCase())
            }
            maxLength={3}
          />
        </label>
        <label>
          Rows
          <select
            aria-label="Page size"
            value={search.get("size") ?? "50"}
            onChange={(e) => change("size", e.target.value)}
          >
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
          </select>
        </label>
      </div>
      {message && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
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
          {page?.entries.map((entry) => (
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
