import { useEffect, useRef, useState } from "react";
import { api, type Session, type Settlement } from "../api/client";
import { apiErrorMessage } from "../settlement/model";

function settlementFromHash(): string | undefined {
  const match = window.location.hash.match(/^#settlement-([^/]+)$/);
  return match?.[1];
}

export function useSettlementDetail(session: Session) {
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
        ) {
          return;
        }
        setSettlement(nextSettlement);
      })
      .catch((cause) => {
        if (
          controller.signal.aborted ||
          requestId !== detailRequestId.current ||
          settlementFromHash() !== requestKey
        ) {
          return;
        }
        setSettlement(undefined);
        setMessage(
          apiErrorMessage(cause, "Could not load the settlement detail."),
        );
      });
    return () => controller.abort();
  }, [settlementId, session.accessToken]);

  return { message, settlement, settlementId };
}
