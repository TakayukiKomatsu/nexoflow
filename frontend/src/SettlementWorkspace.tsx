import type { PricingQuote, Session } from "./api/client";
import { SettlementDetail } from "./components/SettlementDetail";
import { SettlementIntent } from "./components/SettlementIntent";
import { StatementLedger } from "./components/StatementLedger";
import { useSettlementIntent } from "./hooks/useSettlementIntent";

export { SETTLEMENT_TIMEOUT_MS } from "./hooks/useSettlementIntent";
export { LEDGER_FILTER_DEBOUNCE_MS } from "./hooks/useStatementFilters";

export type SettlementWorkspaceProps = {
  session: Session;
  quotes: PricingQuote[];
  /** @deprecated Session expiry is owned by the shared API/session module. */
  onExpired?: () => void;
  onSettled?: (consumedQuoteIds: string[]) => void;
  showLedger?: boolean;
};

export function SettlementWorkspace({
  session,
  quotes,
  onSettled,
  showLedger = true,
}: SettlementWorkspaceProps) {
  const intent = useSettlementIntent({ session, onSettled });

  return (
    <>
      <SettlementIntent quotes={quotes} workflow={intent} />
      <SettlementDetail session={session} />
      {showLedger && (
        <StatementLedger
          session={session}
          refreshRevision={intent.ledgerRevision}
        />
      )}
    </>
  );
}
