import type { PricingWorkflow } from "../hooks/usePricingWorkflow";
import type { Currency, ProductType } from "../api/client";

type PricingFormProps = Pick<
  PricingWorkflow,
  | "alertRef"
  | "canCreate"
  | "createQuote"
  | "createReceivable"
  | "feedback"
  | "fieldErrors"
  | "quotePending"
  | "receivableId"
  | "receivablePending"
  | "set"
  | "values"
>;

export function PricingForm({
  alertRef,
  canCreate,
  createQuote,
  createReceivable,
  feedback,
  fieldErrors,
  quotePending,
  receivableId,
  receivablePending,
  set,
  values,
}: PricingFormProps) {
  return (
    <section className="card" aria-labelledby="invoice-title">
      <h2 id="invoice-title">Receivable inputs</h2>
      <div className="form-grid">
        <label>
          Product
          <select
            value={values.productType}
            onChange={(event) =>
              set("productType", event.target.value as ProductType)
            }
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
            onChange={(event) => set("faceAmount", event.target.value)}
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
          <select
            id="faceCurrency"
            value={values.faceCurrency}
            onChange={(event) =>
              set("faceCurrency", event.target.value as Currency)
            }
            aria-invalid={!!fieldErrors.faceCurrency}
            aria-describedby={
              fieldErrors.faceCurrency ? "faceCurrency-error" : undefined
            }
          >
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
          {fieldErrors.faceCurrency && (
            <p id="faceCurrency-error" className="field-error">
              {fieldErrors.faceCurrency}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor="settlementCurrency">Settlement currency</label>
          <select
            id="settlementCurrency"
            value={values.settlementCurrency}
            onChange={(event) =>
              set("settlementCurrency", event.target.value as Currency)
            }
            aria-invalid={!!fieldErrors.settlementCurrency}
            aria-describedby={
              fieldErrors.settlementCurrency
                ? "settlementCurrency-error"
                : undefined
            }
          >
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
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
            onChange={(event) => set("issueDate", event.target.value)}
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
            onChange={(event) => set("dueDate", event.target.value)}
            aria-invalid={!!fieldErrors.dueDate}
            aria-describedby={fieldErrors.dueDate ? "dueDate-error" : undefined}
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
            onChange={(event) => set("assignorId", event.target.value)}
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
        Registering is separate from pricing and requires an existing assignor.
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
  );
}
