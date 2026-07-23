import "./App.css";
import { Login } from "./components/Login";
import { PricingWorkspace } from "./components/PricingWorkspace";
import { useSessionLifecycle } from "./hooks/useSessionLifecycle";

export { SIMULATION_DEBOUNCE_MS } from "./hooks/useLiveSimulation";

export default function App() {
  const { end, establish, session } = useSessionLifecycle();
  return session ? (
    <PricingWorkspace session={session} onSignOut={end} />
  ) : (
    <Login onSession={establish} />
  );
}
