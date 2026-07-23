import { expireSession, type Session } from "../session";

export type { Session } from "../session";

export type Uuid = string;
export type IsoDate = string;
export type IsoInstant = string;
export type Int32 = number;
export type Int64 = number;

export type AccessToken = {
  accessToken: string;
  expiresIn: Int64;
  tokenType: string;
};
export type CurrentUser = {
  id: Uuid;
  email: string;
  roles: Session["roles"];
};
export type Receivable = {
  id: Uuid;
  assignorId: Uuid;
  productType: string;
  faceAmount: string;
  faceCurrency: string;
  issueDate: IsoDate;
  dueDate: IsoDate;
  status: string;
  version: Int64;
};

export type PricingSimulationRequest = {
  faceAmount: string;
  faceCurrency: string;
  productType: string;
  dueDate: IsoDate;
  settlementCurrency: string;
};
export type PricingBreakdown = {
  faceAmount: string;
  faceCurrency: string;
  settlementCurrency: string;
  baseRate: string;
  spread: string;
  strategyCode: string;
  dayCountConvention: string;
  termInMonths: string;
  discountedAmount: string;
  fxBaseCurrency: string;
  fxQuoteCurrency: string;
  fxRate: string;
  fxSource: string;
  fxObservedAt: IsoInstant;
  settlementAmount: string;
  pricedAt: IsoInstant;
};
export type PricingSimulation = PricingBreakdown;

export type PricingQuote = {
  id: Uuid;
  receivableId: Uuid;
  productType: string;
  dueDate: IsoDate;
  pricing: PricingBreakdown;
  expiresAt: IsoInstant;
  status: string;
  createdBy: string;
};
export type SettlementItem = {
  quoteId: Uuid;
  receivableId: Uuid;
  settlementAmount: string;
};
export type SettlementPreview = {
  items: SettlementItem[];
  settlementCurrency: string;
  totalAmount: string;
  asOf: IsoInstant;
  earliestExpiry: IsoInstant;
};
export type Settlement = {
  settlementId: Uuid;
  status: string;
  items: SettlementItem[];
  settlementCurrency: string;
  totalAmount: string;
  completedAt: IsoInstant;
};
export type StatementEntry = {
  entryId: Uuid;
  entryType: "SETTLEMENT" | "REVERSAL";
  signedAmount: string;
  effectiveAt: IsoInstant;
  settlementId: Uuid;
  reversalId: Uuid | null;
  assignorId: Uuid;
  assetCurrency: string;
  settlementCurrency: string;
  productType: string;
  receivableId: Uuid;
};
export type StatementPage = {
  entries: StatementEntry[];
  page: Int32;
  size: Int32;
  hasNext: boolean;
};

export type ProblemDetail = {
  status?: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  violations?: Array<{ field: string; message: string }>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly correlationId?: string;

  constructor(
    status: number,
    code: string | undefined,
    message: string,
    correlationId?: string,
  ) {
    super(message);
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
  }
}
const API_ROOT = import.meta.env.VITE_API_ROOT ?? "/api/v1";

async function request<T>(
  path: string,
  init: RequestInit = {},
  token?: string,
): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => undefined)) as
      ProblemDetail | undefined;
    const error = new ApiError(
      response.status,
      body?.code,
      body?.detail ?? `Request failed (${response.status}).`,
      body?.correlationId,
    );
    if (response.status === 401) expireSession();
    throw error;
  }
  return response.json() as Promise<T>;
}

export const api = {
  async login(input: { email: string; password: string }): Promise<Session> {
    const token = await request<AccessToken>("/auth/login", {
      method: "POST",
      body: JSON.stringify(input),
    });
    const me = await request<CurrentUser>("/users/me", {}, token.accessToken);
    return {
      accessToken: token.accessToken,
      expiresAt: Date.now() + token.expiresIn * 1000,
      email: me.email,
      roles: me.roles,
    };
  },
  simulate(
    input: PricingSimulationRequest,
    token: string,
    signal: AbortSignal,
  ) {
    return request<PricingSimulation>(
      "/pricing-simulations",
      { method: "POST", body: JSON.stringify(input), signal },
      token,
    );
  },
  createReceivable(
    input: PricingSimulationRequest & { assignorId: string; issueDate: string },
    token: string,
  ) {
    return request<Receivable>(
      "/receivables",
      {
        method: "POST",
        body: JSON.stringify({
          assignorId: input.assignorId,
          productType: input.productType,
          faceAmount: input.faceAmount,
          faceCurrency: input.faceCurrency,
          issueDate: input.issueDate,
          dueDate: input.dueDate,
        }),
      },
      token,
    );
  },
  createQuote(receivableId: string, settlementCurrency: string, token: string) {
    return request<PricingQuote>(
      "/pricing-quotes",
      {
        method: "POST",
        body: JSON.stringify({ receivableId, settlementCurrency }),
      },
      token,
    );
  },
  previewSettlement(quoteIds: string[], token: string, signal?: AbortSignal) {
    return request<SettlementPreview>(
      "/settlement-previews",
      { method: "POST", body: JSON.stringify({ quoteIds }), signal },
      token,
    );
  },
  settle(
    quoteIds: string[],
    idempotencyKey: string,
    token: string,
    signal?: AbortSignal,
  ) {
    return request<Settlement>(
      "/settlements",
      {
        method: "POST",
        body: JSON.stringify({ quoteIds }),
        headers: { "Idempotency-Key": idempotencyKey },
        signal,
      },
      token,
    );
  },
  settlement(settlementId: string, token: string, signal?: AbortSignal) {
    return request<Settlement>(
      `/settlements/${encodeURIComponent(settlementId)}`,
      { signal },
      token,
    );
  },
  statement(search: URLSearchParams, token: string, signal?: AbortSignal) {
    const suffix = search.toString();
    return request<StatementPage>(
      `/settlement-statements${suffix ? `?${suffix}` : ""}`,
      { signal },
      token,
    );
  },
};
