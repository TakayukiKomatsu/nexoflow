import { useCallback, useEffect, useState } from "react";
import type { Session } from "../api/client";
import {
  clearSession,
  expireSession,
  loadSession,
  storeSession,
  subscribeToSessionExpiry,
} from "../session";

export function useSessionLifecycle() {
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
  return { end, establish, session };
}
