import { Component, type ReactNode } from "react";

export const UI_RENDER_FAILURE = "SRM UI render failure" as const;

type Props = {
  children: ReactNode;
  onError?: (message: typeof UI_RENDER_FAILURE) => void;
  onReset?: () => void;
};

type State = { failed: boolean };

function reportRenderFailure(message: typeof UI_RENDER_FAILURE): void {
  console.error(message);
}

export class ApplicationErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(): void {
    (this.props.onError ?? reportRenderFailure)(UI_RENDER_FAILURE);
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
