import type { Session } from "../api/client";
import { useSettlementDetail } from "../hooks/useSettlementDetail";

export function SettlementDetail({ session }: { session: Session }) {
  const { message, settlement, settlementId } = useSettlementDetail(session);
  if (!settlementId) return null;
  return (
    <section
      className="card settlement-detail"
      aria-labelledby="settlement-detail-title"
      aria-busy={!settlement && !message}
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
        <p role="status">Loading settlement…</p>
      )}
    </section>
  );
}
