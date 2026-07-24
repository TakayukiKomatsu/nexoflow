import { useState, type FormEvent } from "react";
import { ApiError, api, type Session } from "../api/client";

export function Login({
  onSession,
}: {
  onSession: (session: Session) => void;
}) {
  const [email, setEmail] = useState("operator@srm.local");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(undefined);
    try {
      onSession(await api.login({ email, password }));
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : "Unable to sign in.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <form
        className="card"
        onSubmit={submit}
        aria-describedby={error ? "login-error" : undefined}
        aria-busy={loading}
      >
        <p className="eyebrow">SRM Credit Engine</p>
        <h1>Operator sign in</h1>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <p id="login-error" className="error" role="alert">
            {error}
          </p>
        )}
        <button disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
