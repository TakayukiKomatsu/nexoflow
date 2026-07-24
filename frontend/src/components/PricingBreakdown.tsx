import type { PricingWorkflow } from "../hooks/usePricingWorkflow";

type PricingBreakdownProps = Pick<
  PricingWorkflow,
  "quotes" | "simulation" | "state"
>;

export function PricingBreakdown({
  quotes,
  simulation,
  state,
}: PricingBreakdownProps) {
  return (
    <section
      className="card result"
      aria-labelledby="price-title"
      aria-busy={state === "loading" || state === "stale"}
    >
      <h2 id="price-title">Server simulation</h2>
      {simulation ? (
        <div
          className={
            state === "stale" || state === "error" ? "stale-result" : undefined
          }
          aria-label={state === "error" ? "Stale server simulation" : undefined}
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
                  {quote.pricing.fxBaseCurrency}/{quote.pricing.fxQuoteCurrency}
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
              <div>
                <dt>Created by</dt>
                <dd>{quote.createdBy}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>
    </section>
  );
}
