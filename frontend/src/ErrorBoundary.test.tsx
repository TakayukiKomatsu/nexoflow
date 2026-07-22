import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApplicationErrorBoundary } from "./ErrorBoundary";

function BrokenModule(): never {
  throw new Error("render failed");
}

it("offers an accessible recovery action when an unexpected render fails", () => {
  vi.spyOn(console, "error").mockImplementation(() => undefined);
  const recover = vi.fn();

  render(
    <ApplicationErrorBoundary onReset={recover}>
      <BrokenModule />
    </ApplicationErrorBoundary>,
  );

  expect(screen.getByRole("alert")).toHaveTextContent(
    "The application could not continue",
  );
  fireEvent.click(screen.getByRole("button", { name: "Reload application" }));
  expect(recover).toHaveBeenCalledOnce();
});
