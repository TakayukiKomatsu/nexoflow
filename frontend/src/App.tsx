import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";
import {
  ApiError,
  api,
  type PricingSimulation,
  type PricingSimulationRequest,
  type PricingQuote,
  type Session,
} from "./api/client";
import {
  clearSession,
  expireSession,
  loadSession,
  storeSession,
  subscribeToSessionExpiry,
} from "./session";
import { SettlementWorkspace } from "./SettlementWorkspace";
import "./App.css";

export const SIMULATION_DEBOUNCE_MS = 300;

type FormValues = PricingSimulationRequest & {
  assignorId: string;
  issueDate: string;
};

const initialValues: FormValues = {
  assignorId: "",
  faceAmount: "1000.00",
  faceCurrency: "BRL",
  productType: "MERCANTILE_INVOICE",
  issueDate: new Date().toISOString().slice(0, 10),
  dueDate: "2030-02-14",
  settlementCurrency: "BRL",
};

type FieldErrors = Partial<Record<keyof FormValues, string>>;

type Feedback = {
  text: string;
  kind: "success" | "error";
};

function pricingValidation(values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (
    !/^\d{1,15}(\.\d{1,4})?$/.test(values.faceAmount) ||
    Number(values.faceAmount) <= 0
  ) {
    errors.faceAmount =
      "Enter a positive amount with up to four decimal places.";
  }
  if (!/^[A-Z]{3}$/.test(values.faceCurrency)) {
    errors.faceCurrency = "Enter a three-letter uppercase face currency.";
  }
  if (!/^[A-Z]{3}$/.test(values.settlementCurrency)) {
    errors.settlementCurrency =
      "Enter a three-letter uppercase settlement currency.";
  }
  if (!values.dueDate) errors.dueDate = "Enter a due date.";
  return errors;
}

function registrationValidation(values: FormValues): FieldErrors {
  const errors = pricingValidation(values);
  if (!values.issueDate) errors.issueDate = "Enter an issue date.";
  if (
    values.issueDate &&
    values.dueDate &&
    values.dueDate <= values.issueDate
  ) {
    errors.dueDate = "Due date must be after issue date.";
  }
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      values.assignorId,
    )
  ) {
    errors.assignorId = "Enter a valid assignor UUID.";
  }
  return errors;
}

function fingerprint(values: FormValues): string {
  return JSON.stringify([
    values.assignorId,
    values.productType,
    values.faceAmount,
    values.faceCurrency,
    values.issueDate,
    values.dueDate,
    values.settlementCurrency,
  ]);
}

function Login({ onSession }: { onSession: (session: Session) => void }) {
  const [email, setEmail] = useState("operator@srm.local");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(undefined);
    try {
      onSession(await api.login({ email, password }));
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "Unable to sign in.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <form
        className="card"
        onSubmit={submit}
        aria-describedby={error ? "login-error" : undefined}
      >
        <p className="eyebrow">SRM Credit Engine</p>
        <h1>Operator sign in</h1>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <p id="login-error" className="error" role="alert">
            {error}
          </p>
        )}
        <button disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}

function Workflow({
  session,
  onExpired,
  onSignOut,
}: {
  session: Session;
  onExpired: () => void;
  onSignOut: () => void;
}) {
  const [values, setValues] = useState(initialValues);
  const [simulation, setSimulation] = useState<PricingSimulation>();
  const [quotes, setQuotes] = useState<PricingQuote[]>([]);
  const [receivableId, setReceivableId] = useState<string>();
  const [receivablePending, setReceivablePending] = useState(false);
  const [quotePending, setQuotePending] = useState(false);
  const [showAssignorError, setShowAssignorError] = useState(false);
  const [focusAlert, setFocusAlert] = useState(false);
  const [state, setState] = useState<"idle" | "loading" | "stale" | "error">(
    "idle",
  );
  const [feedback, setFeedback] = useState<Feedback>();
  const requestId = useRef(0);
  const simulationRef = useRef<PricingSimulation | undefined>(undefined);
  const receivableIdRef = useRef<string | undefined>(undefined);
  const receivablePendingRef = useRef(false);
  const quotePendingRef = useRef(false);
  const formFingerprintRef = useRef(fingerprint(initialValues));
  const alertRef = useRef<HTMLParagraphElement>(null);
  const pricingErrors = useMemo(() => pricingValidation(values), [values]);
  const registrationErrors = useMemo(
    () => registrationValidation(values),
    [values],
  );
  const fieldErrors = {
    ...registrationErrors,
    assignorId: showAssignorError ? registrationErrors.assignorId : undefined,
  };
  const simulationError = Object.values(pricingErrors)[0];
  const { faceAmount, faceCurrency, productType, dueDate, settlementCurrency } =
    values;
  const canCreate =
    session.roles.includes("OPERATOR") || session.roles.includes("ADMIN");
  const canViewLedger = session.roles.some((role) =>
    ["OPERATOR", "ANALYST", "ADMIN", "AUDITOR"].includes(role),
  );

  useEffect(() => {
    if (focusAlert && feedback?.kind === "error") {
      alertRef.current?.focus();
      setFocusAlert(false);
    }
  }, [focusAlert, feedback]);

  useEffect(() => {
    const currentRequest = ++requestId.current;
    if (!canCreate) {
      setSimulation(undefined);
      simulationRef.current = undefined;
      setState("idle");
      setFeedback(undefined);
      return;
    }
    if (simulationError) {
      setSimulation(undefined);
      simulationRef.current = undefined;
      setState("idle");
      return;
    }
    setState(simulationRef.current ? "stale" : "loading");
    setFeedback(undefined);
    const controller = new AbortController();
    const timeout = window.setTimeout(async () => {
      try {
        const result = await api.simulate(
          {
            faceAmount,
            faceCurrency,
            productType,
            dueDate,
            settlementCurrency,
          },
          session.accessToken,
          controller.signal,
        );
        if (currentRequest === requestId.current) {
          simulationRef.current = result;
          setSimulation(result);
          setState("idle");
        }
      } catch (cause) {
        if (controller.signal.aborted || currentRequest !== requestId.current)
          return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setFocusAlert(true);
        setState("error");
        setFeedback({
          text:
            cause instanceof ApiError
              ? cause.message
              : "Simulation is unavailable.",
          kind: "error",
        });
      }
    }, SIMULATION_DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [
    faceAmount,
    faceCurrency,
    productType,
    dueDate,
    settlementCurrency,
    simulationError,
    canCreate,
    session.accessToken,
    onExpired,
  ]);

  function set<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    const next = { ...values, [key]: value };
    formFingerprintRef.current = fingerprint(next);
    setValues(next);
    receivableIdRef.current = undefined;
    setReceivableId(undefined);
    if (key === "assignorId") setShowAssignorError(false);
    setFeedback(undefined);
    setFocusAlert(false);
  }

  async function createReceivable() {
    if (receivablePendingRef.current) return;
    const errors = registrationValidation(values);
    setShowAssignorError(true);
    const firstInvalidField = (
      [
        "faceAmount",
        "faceCurrency",
        "settlementCurrency",
        "issueDate",
        "dueDate",
        "assignorId",
      ] as const
    ).find((key) => errors[key]);
    if (firstInvalidField) {
      setFeedback({ text: errors[firstInvalidField]!, kind: "error" });
      document.getElementById(firstInvalidField)?.focus();
      return;
    }
    const requestFingerprint = formFingerprintRef.current;
    receivablePendingRef.current = true;
    setReceivablePending(true);
    setFeedback(undefined);
    try {
      const receivable = await api.createReceivable(
        values,
        session.accessToken,
      );
      if (requestFingerprint !== formFingerprintRef.current) return;
      receivableIdRef.current = receivable.id;
      setReceivableId(receivable.id);
      setFeedback({
        text: `Receivable ${receivable.id} registered. You can now create a quote.`,
        kind: "success",
      });
    } catch (cause) {
      if (requestFingerprint !== formFingerprintRef.current) return;
      setFeedback({
        text:
          cause instanceof ApiError
            ? cause.message
            : "Could not register receivable.",
        kind: "error",
      });
      setFocusAlert(true);
    } finally {
      receivablePendingRef.current = false;
      setReceivablePending(false);
    }
  }

  async function createQuote() {
    if (!receivableId || quotePendingRef.current) return;
    const requestFingerprint = formFingerprintRef.current;
    const requestReceivableId = receivableId;
    quotePendingRef.current = true;
    setQuotePending(true);
    setFeedback(undefined);
    try {
      const result = await api.createQuote(
        requestReceivableId,
        values.settlementCurrency,
        session.accessToken,
      );
      setQuotes((current) => [...current, result]);
      if (
        requestFingerprint !== formFingerprintRef.current ||
        requestReceivableId !== receivableIdRef.current
      )
        return;
      setFeedback({
        text: `Quote ${result.id} created; it expires at ${result.expiresAt}.`,
        kind: "success",
      });
    } catch (cause) {
      if (
        requestFingerprint !== formFingerprintRef.current ||
        requestReceivableId !== receivableIdRef.current
      )
        return;
      setFeedback({
        text:
          cause instanceof ApiError ? cause.message : "Could not create quote.",
        kind: "error",
      });
      setFocusAlert(true);
    } finally {
      quotePendingRef.current = false;
      setQuotePending(false);
    }
  }

  return (
    <main className="workflow">
      <header>
        <div>
          <p className="eyebrow">SRM Credit Engine</p>
          <h1>Live receivable pricing</h1>
        </div>
        <div className="session">
          <span>{session.email}</span>
          <button className="secondary" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </header>
      <p className="notice" role="status">
        {state === "loading"
          ? "Requesting authoritative price…"
          : state === "stale"
            ? "Inputs changed — displayed result is stale while the server updates it."
            : state === "error"
              ? simulation
                ? "Latest server refresh failed — displayed values are stale."
                : "Latest server simulation failed."
              : "Prices are calculated by the server; typing never creates a quote or settlement."}
      </p>
      <div className="layout">
        <section className="card" aria-labelledby="invoice-title">
          <h2 id="invoice-title">Receivable inputs</h2>
          <div className="form-grid">
            <label>
              Product
              <select
                value={values.productType}
                onChange={(e) => set("productType", e.target.value)}
              >
                <option value="MERCANTILE_INVOICE">Invoice</option>
                <option value="POST_DATED_CHEQUE">Post-dated cheque</option>
              </select>
            </label>
            <div className="field">
              <label htmlFor="faceAmount">Face amount</label>
              <input
                id="faceAmount"
                inputMode="decimal"
                value={values.faceAmount}
                onChange={(e) => set("faceAmount", e.target.value)}
                aria-invalid={!!fieldErrors.faceAmount}
                aria-describedby={
                  fieldErrors.faceAmount ? "faceAmount-error" : undefined
                }
              />
              {fieldErrors.faceAmount && (
                <p id="faceAmount-error" className="field-error">
                  {fieldErrors.faceAmount}
                </p>
              )}
            </div>
            <div className="field">
              <label htmlFor="faceCurrency">Face currency</label>
              <input
                id="faceCurrency"
                maxLength={3}
                value={values.faceCurrency}
                onChange={(e) =>
                  set("faceCurrency", e.target.value.toUpperCase())
                }
                aria-invalid={!!fieldErrors.faceCurrency}
                aria-describedby={
                  fieldErrors.faceCurrency ? "faceCurrency-error" : undefined
                }
              />
              {fieldErrors.faceCurrency && (
                <p id="faceCurrency-error" className="field-error">
                  {fieldErrors.faceCurrency}
                </p>
              )}
            </div>
            <div className="field">
              <label htmlFor="settlementCurrency">Settlement currency</label>
              <input
                id="settlementCurrency"
                maxLength={3}
                value={values.settlementCurrency}
                onChange={(e) =>
                  set("settlementCurrency", e.target.value.toUpperCase())
                }
                aria-invalid={!!fieldErrors.settlementCurrency}
                aria-describedby={
                  fieldErrors.settlementCurrency
                    ? "settlementCurrency-error"
                    : undefined
                }
              />
              {fieldErrors.settlementCurrency && (
                <p id="settlementCurrency-error" className="field-error">
                  {fieldErrors.settlementCurrency}
                </p>
              )}
            </div>
            <div className="field">
              <label htmlFor="issueDate">Issue date</label>
              <input
                id="issueDate"
                type="date"
                value={values.issueDate}
                onChange={(e) => set("issueDate", e.target.value)}
                aria-invalid={!!fieldErrors.issueDate}
                aria-describedby={
                  fieldErrors.issueDate ? "issueDate-error" : undefined
                }
              />
              {fieldErrors.issueDate && (
                <p id="issueDate-error" className="field-error">
                  {fieldErrors.issueDate}
                </p>
              )}
            </div>
            <div className="field">
              <label htmlFor="dueDate">Due date</label>
              <input
                id="dueDate"
                type="date"
                value={values.dueDate}
                onChange={(e) => set("dueDate", e.target.value)}
                aria-invalid={!!fieldErrors.dueDate}
                aria-describedby={
                  fieldErrors.dueDate ? "dueDate-error" : undefined
                }
              />
              {fieldErrors.dueDate && (
                <p id="dueDate-error" className="field-error">
                  {fieldErrors.dueDate}
                </p>
              )}
            </div>
            <div className="field wide">
              <label htmlFor="assignorId">
                Assignor ID (for explicit registration)
              </label>
              <input
                id="assignorId"
                value={values.assignorId}
                onChange={(e) => set("assignorId", e.target.value)}
                aria-invalid={!!fieldErrors.assignorId}
                aria-describedby={
                  fieldErrors.assignorId
                    ? "assignor-help assignorId-error"
                    : "assignor-help"
                }
              />
              {fieldErrors.assignorId && (
                <p id="assignorId-error" className="field-error">
                  {fieldErrors.assignorId}
                </p>
              )}
            </div>
          </div>
          <p id="assignor-help" className="hint">
            Registering is separate from pricing and requires an existing
            assignor.
          </p>
          {feedback && (
            <p
              id="workflow-feedback"
              className={feedback.kind === "error" ? "error" : "success"}
              role={feedback.kind === "error" ? "alert" : "status"}
              tabIndex={feedback.kind === "error" ? -1 : undefined}
              ref={feedback.kind === "error" ? alertRef : undefined}
            >
              {feedback.text}
            </p>
          )}
          <button
            onClick={createReceivable}
            disabled={!canCreate || receivablePending}
          >
            {receivablePending ? "Registering…" : "Register receivable"}
          </button>
          <button
            className="secondary quote-button"
            onClick={createQuote}
            disabled={!canCreate || !receivableId || quotePending}
          >
            {quotePending ? "Creating quote…" : "Create quote"}
          </button>
          {!canCreate && (
            <p className="error" role="alert">
              Your role cannot create receivables.
            </p>
          )}
        </section>
        <section
          className="card result"
          aria-labelledby="price-title"
          aria-busy={state === "loading" || state === "stale"}
        >
          <h2 id="price-title">Server simulation</h2>
          {simulation ? (
            <div
              className={
                state === "stale" || state === "error"
                  ? "stale-result"
                  : undefined
              }
              aria-label={
                state === "error" ? "Stale server simulation" : undefined
              }
            >
              <p className="amount">
                <span>Settlement amount</span>
                <output>{simulation.settlementAmount}</output>{" "}
                <span>{simulation.settlementCurrency}</span>
              </p>
              <dl>
                <div>
                  <dt>Discounted amount</dt>
                  <dd>{simulation.discountedAmount}</dd>
                </div>
                <div>
                  <dt>Base rate</dt>
                  <dd>{simulation.baseRate}</dd>
                </div>
                <div>
                  <dt>Spread</dt>
                  <dd>{simulation.spread}</dd>
                </div>
                <div>
                  <dt>Term in months</dt>
                  <dd>{simulation.termInMonths}</dd>
                </div>
                <div>
                  <dt>FX rate</dt>
                  <dd>{simulation.fxRate}</dd>
                </div>
                <div>
                  <dt>FX pair</dt>
                  <dd>
                    {simulation.fxBaseCurrency}/{simulation.fxQuoteCurrency}
                  </dd>
                </div>
                <div>
                  <dt>FX source</dt>
                  <dd>{simulation.fxSource}</dd>
                </div>
                <div>
                  <dt>Strategy</dt>
                  <dd>{simulation.strategyCode}</dd>
                </div>
              </dl>
            </div>
          ) : (
            <p className="empty">
              Enter valid pricing inputs to request a server simulation.
            </p>
          )}
          <div className="quote-history">
            {quotes.map((quote) => (
              <article
                className="quote-breakdown"
                key={quote.id}
                aria-labelledby={`quote-${quote.id}`}
              >
                <h3 id={`quote-${quote.id}`}>Quote {quote.id}</h3>
                <dl>
                  <div>
                    <dt>Product type</dt>
                    <dd>{quote.productType}</dd>
                  </div>
                  <div>
                    <dt>Due date</dt>
                    <dd>{quote.dueDate}</dd>
                  </div>
                  <div>
                    <dt>Face amount</dt>
                    <dd>
                      {quote.pricing.faceAmount} {quote.pricing.faceCurrency}
                    </dd>
                  </div>
                  <div>
                    <dt>Base rate</dt>
                    <dd>{quote.pricing.baseRate}</dd>
                  </div>
                  <div>
                    <dt>Spread</dt>
                    <dd>{quote.pricing.spread}</dd>
                  </div>
                  <div>
                    <dt>Strategy</dt>
                    <dd>{quote.pricing.strategyCode}</dd>
                  </div>
                  <div>
                    <dt>Day count</dt>
                    <dd>{quote.pricing.dayCountConvention}</dd>
                  </div>
                  <div>
                    <dt>Term in months</dt>
                    <dd>{quote.pricing.termInMonths}</dd>
                  </div>
                  <div>
                    <dt>Discounted amount</dt>
                    <dd>{quote.pricing.discountedAmount}</dd>
                  </div>
                  <div>
                    <dt>FX pair</dt>
                    <dd>
                      {quote.pricing.fxBaseCurrency}/
                      {quote.pricing.fxQuoteCurrency}
                    </dd>
                  </div>
                  <div>
                    <dt>FX rate</dt>
                    <dd>{quote.pricing.fxRate}</dd>
                  </div>
                  <div>
                    <dt>FX source</dt>
                    <dd>{quote.pricing.fxSource}</dd>
                  </div>
                  <div>
                    <dt>FX observed at</dt>
                    <dd>{quote.pricing.fxObservedAt}</dd>
                  </div>
                  <div>
                    <dt>Settlement amount</dt>
                    <dd>
                      {quote.pricing.settlementAmount}{" "}
                      {quote.pricing.settlementCurrency}
                    </dd>
                  </div>
                  <div>
                    <dt>Priced at</dt>
                    <dd>{quote.pricing.pricedAt}</dd>
                  </div>
                  <div>
                    <dt>Expires at</dt>
                    <dd>{quote.expiresAt}</dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>{quote.status}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        </section>
      </div>
      <SettlementWorkspace
        session={session}
        quotes={quotes}
        onExpired={onExpired}
        showLedger={canViewLedger}
      />
    </main>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | undefined>(loadSession);
  useEffect(() => subscribeToSessionExpiry(() => setSession(undefined)), []);
  useEffect(() => {
    if (!session) return;
    const remaining = session.expiresAt - Date.now();
    if (remaining <= 0) {
      expireSession();
      return;
    }
    const timeout = window.setTimeout(expireSession, remaining);
    return () => window.clearTimeout(timeout);
  }, [session]);
  const establish = useCallback((next: Session) => {
    storeSession(next);
    setSession(next);
  }, []);
  const end = useCallback(() => {
    clearSession();
    setSession(undefined);
  }, []);
  return session ? (
    <Workflow session={session} onExpired={end} onSignOut={end} />
  ) : (
    <Login onSession={establish} />
  );
}
