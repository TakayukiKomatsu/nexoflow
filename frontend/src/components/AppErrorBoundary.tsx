import { Component, type ReactNode } from "react";
import {
  reportUiRenderFailure,
  UI_RENDER_FAILURE,
} from "../observability/SafeUiLogger.ts";

export { UI_RENDER_FAILURE } from "../observability/SafeUiLogger.ts";

type Props = {
  children: ReactNode;
  onError?: (message: typeof UI_RENDER_FAILURE) => void;
  onReset?: () => void;
};

type State = { failed: boolean };

export class ApplicationErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(): void {
    if (this.props.onError) {
      this.props.onError(UI_RENDER_FAILURE);
      return;
    }
    reportUiRenderFailure();
  }

  private reset = () => {
    if (this.props.onReset) {
      this.props.onReset();
      this.setState({ failed: false });
      return;
    }
    window.location.reload();
  };

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <main className="login-page">
        <section className="card" aria-labelledby="fatal-error-title">
          <div role="alert">
            <p className="eyebrow">SRM Credit Engine</p>
            <h1 id="fatal-error-title">The application could not continue</h1>
            <p>
              Your last confirmed server operation is not changed. Reload the
              application before trying again.
            </p>
          </div>
          <button onClick={this.reset}>Reload application</button>
        </section>
      </main>
    );
  }
}
