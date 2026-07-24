export const SESSION_STORAGE_KEY = "srm-session";
export const SESSION_EXPIRED_EVENT = "srm:session-expired";

export const ACTOR_ROLES = ["OPERATOR", "ANALYST", "ADMIN", "AUDITOR"] as const;

export type ActorRole = (typeof ACTOR_ROLES)[number];

export type Session = {
  accessToken: string;
  expiresAt: number;
  email: string;
  roles: ActorRole[];
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isActorRole(value: unknown): value is ActorRole {
  return (
    typeof value === "string" &&
    (ACTOR_ROLES as readonly string[]).includes(value)
  );
}

export function parseSession(
  value: unknown,
  now: number = Date.now(),
): Session | undefined {
  if (!isRecord(value)) return undefined;
  const { accessToken, expiresAt, email, roles } = value;
  if (
    typeof accessToken !== "string" ||
    accessToken.length === 0 ||
    accessToken.length > 16_384 ||
    typeof expiresAt !== "number" ||
    !Number.isFinite(expiresAt) ||
    expiresAt <= now ||
    typeof email !== "string" ||
    email.length === 0 ||
    email.length > 254 ||
    !email.includes("@") ||
    !Array.isArray(roles) ||
    roles.length === 0 ||
    roles.length > ACTOR_ROLES.length ||
    !roles.every(isActorRole) ||
    new Set(roles).size !== roles.length
  ) {
    return undefined;
  }
  return { accessToken, expiresAt, email, roles };
}

export function loadSession(): Session | undefined {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return undefined;
    const session = parseSession(JSON.parse(raw));
    if (!session) localStorage.removeItem(SESSION_STORAGE_KEY);
    return session;
  } catch {
    localStorage.removeItem(SESSION_STORAGE_KEY);
    return undefined;
  }
}

export function storeSession(session: Session): void {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_STORAGE_KEY);
}

export function expireSession(): void {
  clearSession();
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

export function subscribeToSessionExpiry(listener: () => void): () => void {
  window.addEventListener(SESSION_EXPIRED_EVENT, listener);
  return () => window.removeEventListener(SESSION_EXPIRED_EVENT, listener);
}
