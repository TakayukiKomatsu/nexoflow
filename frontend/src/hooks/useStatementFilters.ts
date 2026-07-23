import { useEffect, useRef, useState } from "react";
import {
  api,
  statementFiltersFromSearch,
  type Session,
  type StatementPage,
} from "../api/client";
import { apiErrorMessage } from "../settlement/model";

export const LEDGER_FILTER_DEBOUNCE_MS = 300;

export function toLocalDateTimeValue(instant?: string): string {
  if (!instant) return "";
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return "";
  const localTime = new Date(
    date.getTime() - date.getTimezoneOffset() * 60_000,
  );
  return localTime.toISOString().slice(0, 16);
}

export function toUtcInstant(localDateTime: string): string {
  return localDateTime ? new Date(localDateTime).toISOString() : "";
}

export function useStatementFilters(session: Session, refreshRevision: number) {
  const [filters, setFilters] = useState(
    () => new URLSearchParams(window.location.search),
  );
  const [search, setSearch] = useState(
    () => new URLSearchParams(window.location.search),
  );
  const [page, setPage] = useState<StatementPage>();
  const [message, setMessage] = useState<string>();
  const [loading, setLoading] = useState(true);
  const statementRequestId = useRef(0);
  const statementAbort = useRef<AbortController | undefined>(undefined);
  const filterTimeout = useRef<number | undefined>(undefined);

  useEffect(() => {
    const requestId = ++statementRequestId.current;
    const requestKey = search.toString();
    const controller = new AbortController();
    statementAbort.current = controller;
    setPage(undefined);
    setMessage(undefined);
    setLoading(true);
    api
      .statement(
        statementFiltersFromSearch(search),
        session.accessToken,
        controller.signal,
      )
      .then((nextPage) => {
        if (
          controller.signal.aborted ||
          requestId !== statementRequestId.current ||
          new URLSearchParams(window.location.search).toString() !== requestKey
        ) {
          return;
        }
        setPage(nextPage);
      })
      .catch((cause) => {
        if (
          controller.signal.aborted ||
          requestId !== statementRequestId.current ||
          new URLSearchParams(window.location.search).toString() !== requestKey
        ) {
          return;
        }
        setMessage(
          apiErrorMessage(cause, "Could not load the settlement statement."),
        );
      })
      .finally(() => {
        if (requestId === statementRequestId.current) {
          setLoading(false);
          if (statementAbort.current === controller) {
            statementAbort.current = undefined;
          }
        }
      });
    return () => controller.abort();
  }, [search, session.accessToken, refreshRevision]);

  useEffect(() => {
    const restore = () => {
      if (filterTimeout.current !== undefined) {
        window.clearTimeout(filterTimeout.current);
        filterTimeout.current = undefined;
      }
      const restored = new URLSearchParams(window.location.search);
      setFilters(restored);
      setSearch(new URLSearchParams(restored));
    };
    window.addEventListener("popstate", restore);
    return () => {
      window.removeEventListener("popstate", restore);
      if (filterTimeout.current !== undefined) {
        window.clearTimeout(filterTimeout.current);
      }
    };
  }, []);

  function updateLocation(
    next: URLSearchParams,
    mode: "pushState" | "replaceState",
  ) {
    window.history[mode](
      null,
      "",
      `${window.location.pathname}${next.toString() ? `?${next}` : ""}${window.location.hash}`,
    );
  }

  function change(name: string, value: string) {
    const next = new URLSearchParams(filters);
    if (value) next.set(name, value);
    else next.delete(name);
    next.set("page", "0");
    updateLocation(next, "replaceState");
    setFilters(next);
    statementRequestId.current += 1;
    statementAbort.current?.abort();
    setPage(undefined);
    setMessage(undefined);
    setLoading(true);
    if (filterTimeout.current !== undefined) {
      window.clearTimeout(filterTimeout.current);
    }
    filterTimeout.current = window.setTimeout(() => {
      filterTimeout.current = undefined;
      setSearch(new URLSearchParams(next));
    }, LEDGER_FILTER_DEBOUNCE_MS);
  }

  function move(nextPage: number) {
    const next = new URLSearchParams(filters);
    next.set("page", String(nextPage));
    if (filterTimeout.current !== undefined) {
      window.clearTimeout(filterTimeout.current);
      filterTimeout.current = undefined;
    }
    updateLocation(next, "pushState");
    setFilters(next);
    setSearch(new URLSearchParams(next));
  }

  return { change, filters, loading, message, move, page };
}
