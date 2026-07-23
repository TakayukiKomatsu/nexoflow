import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import {
  ApplicationErrorBoundary,
  UI_RENDER_FAILURE,
} from "./ErrorBoundary";

function BrokenModule(): never {
  throw new Error("customer-secret-123");
}

it("offers an accessible recovery action when an unexpected render fails", () => {
  vi.spyOn(console, "error").mockImplementation(() => undefined);
  const recover = vi.fn();
  const report = vi.fn();

  render(
    <ApplicationErrorBoundary onError={report} onReset={recover}>
      <BrokenModule />
    </ApplicationErrorBoundary>,
  );

  expect(screen.getByRole("alert")).toHaveTextContent(
    "The application could not continue",
  );
  fireEvent.click(screen.getByRole("button", { name: "Reload application" }));
  expect(recover).toHaveBeenCalledOnce();
  expect(report).toHaveBeenCalledExactlyOnceWith(UI_RENDER_FAILURE);
  expect(JSON.stringify(report.mock.calls)).not.toContain(
    "customer-secret-123",
  );
});
