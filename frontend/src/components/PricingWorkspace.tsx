import type { Session } from "../api/client";
import { usePricingWorkflow } from "../hooks/usePricingWorkflow";
import { SettlementWorkspace } from "../SettlementWorkspace";
import { PricingBreakdown } from "./PricingBreakdown";
import { PricingForm } from "./PricingForm";

export function PricingWorkspace({
  session,
  onSignOut,
}: {
  session: Session;
  onSignOut: () => void;
}) {
  const workflow = usePricingWorkflow(session);

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
        {workflow.state === "loading"
          ? "Requesting authoritative price…"
          : workflow.state === "stale"
            ? "Inputs changed — displayed result is stale while the server updates it."
            : workflow.state === "error"
              ? workflow.simulation
                ? "Latest server refresh failed — displayed values are stale."
                : "Latest server simulation failed."
              : "Prices are calculated by the server; typing never creates a quote or settlement."}
      </p>
      <div className="layout">
        <PricingForm {...workflow} />
        <PricingBreakdown {...workflow} />
      </div>
      <SettlementWorkspace
        session={session}
        quotes={workflow.quotes}
        onSettled={workflow.consumeQuotes}
        showLedger={workflow.canViewLedger}
      />
    </main>
  );
}
