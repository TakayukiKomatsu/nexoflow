import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import {
  ApplicationErrorBoundary,
  UI_RENDER_FAILURE,
} from "./components/AppErrorBoundary";

let renderFails = true;

function RecoverableModule() {
  if (renderFails) throw new Error("customer-secret-123");
  return <p>Recovered workspace</p>;
}

it("offers an accessible recovery action when an unexpected render fails", () => {
  renderFails = true;
  vi.spyOn(console, "error").mockImplementation(() => undefined);
  const recover = vi.fn(() => {
    renderFails = false;
  });
  const report = vi.fn();

  render(
    <ApplicationErrorBoundary onError={report} onReset={recover}>
      <RecoverableModule />
    </ApplicationErrorBoundary>,
  );

  expect(screen.getByRole("alert")).toHaveTextContent(
    "The application could not continue",
  );
  fireEvent.click(screen.getByRole("button", { name: "Reload application" }));
  expect(recover).toHaveBeenCalledOnce();
  expect(screen.getByText("Recovered workspace")).toBeVisible();
  expect(report).toHaveBeenCalledExactlyOnceWith(UI_RENDER_FAILURE);
  expect(JSON.stringify(report.mock.calls)).not.toContain(
    "customer-secret-123",
  );
});
