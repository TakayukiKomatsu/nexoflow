import { expireSession, type Session } from "../session";

export type { Session } from "../session";

export type Uuid = string;
export type IsoDate = string;
export type IsoInstant = string;
export type Int32 = number;
export type Int64 = number;

export const API_OPERATIONS = {
  login: { method: "POST", path: "/auth/login" },
  currentUser: { method: "GET", path: "/users/me" },
  simulate: { method: "POST", path: "/pricing-simulations" },
  createReceivable: { method: "POST", path: "/receivables" },
  createQuote: { method: "POST", path: "/pricing-quotes" },
  previewSettlement: { method: "POST", path: "/settlement-previews" },
  settle: { method: "POST", path: "/settlements" },
  settlement: { method: "GET", path: "/settlements/{settlementId}" },
  statement: { method: "GET", path: "/settlement-statements" },
} as const;

export type LoginRequest = {
  email: string;
  password: string;
};
export type AccessToken = {
  accessToken: string;
  expiresIn: Int64;
  tokenType: string;
};
export type CurrentUser = {
  id: string;
  email: string;
  roles: Session["roles"];
};
export type ReceivableRequest = {
  assignorId: Uuid;
  productType: string;
  faceAmount: string;
  faceCurrency: string;
  issueDate: IsoDate;
  dueDate: IsoDate;
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
export type QuoteRequest = {
  receivableId: Uuid;
  settlementCurrency: string;
};
export type QuoteIdsRequest = {
  quoteIds: Uuid[];
};
export type SettlementPathParameters = {
  settlementId: Uuid;
};
export type SettlementHeaders = {
  "Idempotency-Key": string;
};
export type StatementFilters = {
  from?: IsoInstant;
  to?: IsoInstant;
  assignorId?: Uuid;
  assetCurrency?: string;
  settlementCurrency?: string;
  productType?: string;
  page?: Int32;
  size?: Int32;
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
type ApiOperation = (typeof API_OPERATIONS)[keyof typeof API_OPERATIONS];
type ApiRequestInit = Omit<RequestInit, "method"> & {
  pathParameters?: Record<string, string>;
  query?: URLSearchParams;
};

function operationPath(
  template: string,
  pathParameters: Record<string, string> = {},
): string {
  return template.replaceAll(/\{([^}]+)\}/g, (_, name: string) => {
    const value = pathParameters[name];
    if (!value) throw new Error(`Missing API path parameter ${name}.`);
    return encodeURIComponent(value);
  });
}

async function request<T>(
  operation: ApiOperation,
  init: ApiRequestInit = {},
  token?: string,
): Promise<T> {
  const { pathParameters, query, ...requestInit } = init;
  const path = operationPath(operation.path, pathParameters);
  const queryString = query?.toString();
  const response = await fetch(
    `${API_ROOT}${path}${queryString ? `?${queryString}` : ""}`,
    {
      ...requestInit,
      method: operation.method,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...requestInit.headers,
      },
    },
  );
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

function optionalInteger(
  search: URLSearchParams,
  name: string,
): number | undefined {
  const value = search.get(name);
  return value === null ? undefined : Number(value);
}

export function statementFiltersFromSearch(
  search: URLSearchParams,
): StatementFilters {
  return {
    from: search.get("from") ?? undefined,
    to: search.get("to") ?? undefined,
    assignorId: search.get("assignorId") ?? undefined,
    assetCurrency: search.get("assetCurrency") ?? undefined,
    settlementCurrency: search.get("settlementCurrency") ?? undefined,
    productType: search.get("productType") ?? undefined,
    page: optionalInteger(search, "page"),
    size: optionalInteger(search, "size"),
  };
}

export const api = {
  async login(input: LoginRequest): Promise<Session> {
    const token = await request<AccessToken>(API_OPERATIONS.login, {
      body: JSON.stringify(input),
    });
    const me = await request<CurrentUser>(
      API_OPERATIONS.currentUser,
      {},
      token.accessToken,
    );
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
      API_OPERATIONS.simulate,
      { body: JSON.stringify(input), signal },
      token,
    );
  },
  createReceivable(input: ReceivableRequest, token: string) {
    const requestBody: ReceivableRequest = {
      assignorId: input.assignorId,
      productType: input.productType,
      faceAmount: input.faceAmount,
      faceCurrency: input.faceCurrency,
      issueDate: input.issueDate,
      dueDate: input.dueDate,
    };
    return request<Receivable>(
      API_OPERATIONS.createReceivable,
      { body: JSON.stringify(requestBody) },
      token,
    );
  },
  createQuote(receivableId: string, settlementCurrency: string, token: string) {
    const input: QuoteRequest = { receivableId, settlementCurrency };
    return request<PricingQuote>(
      API_OPERATIONS.createQuote,
      { body: JSON.stringify(input) },
      token,
    );
  },
  previewSettlement(quoteIds: string[], token: string, signal?: AbortSignal) {
    const input: QuoteIdsRequest = { quoteIds };
    return request<SettlementPreview>(
      API_OPERATIONS.previewSettlement,
      { body: JSON.stringify(input), signal },
      token,
    );
  },
  settle(
    quoteIds: string[],
    idempotencyKey: string,
    token: string,
    signal?: AbortSignal,
  ) {
    const input: QuoteIdsRequest = { quoteIds };
    const headers: SettlementHeaders = { "Idempotency-Key": idempotencyKey };
    return request<Settlement>(
      API_OPERATIONS.settle,
      { body: JSON.stringify(input), headers, signal },
      token,
    );
  },
  settlement(settlementId: string, token: string, signal?: AbortSignal) {
    const pathParameters: SettlementPathParameters = { settlementId };
    return request<Settlement>(
      API_OPERATIONS.settlement,
      { pathParameters: { ...pathParameters }, signal },
      token,
    );
  },
  statement(filters: StatementFilters, token: string, signal?: AbortSignal) {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(filters)) {
      if (value !== undefined) query.set(name, String(value));
    }
    return request<StatementPage>(
      API_OPERATIONS.statement,
      { query, signal },
      token,
    );
  },
};
