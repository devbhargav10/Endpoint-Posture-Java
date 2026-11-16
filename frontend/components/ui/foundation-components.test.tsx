import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { LoadingState, EmptyState, ErrorState, UnauthorizedState } from "./AsyncState";
import { StatusBadge } from "./StatusBadge";
import { ConfirmDialog } from "./ConfirmDialog";

describe("foundation UI states", () => {
  it("renders an announced loading state", () => {
    const markup = renderToStaticMarkup(<LoadingState label="Loading endpoints" />);
    expect(markup).toContain('role="status"');
    expect(markup).toContain("Loading endpoints");
  });

  it("renders empty, error, and unauthorized states with appropriate semantics", () => {
    expect(renderToStaticMarkup(<EmptyState title="No endpoints" />)).toContain("No endpoints");
    expect(renderToStaticMarkup(<ErrorState title="Unavailable" />)).toContain('role="alert"');
    expect(renderToStaticMarkup(<UnauthorizedState />)).toContain("Access restricted");
  });

  it("provides readable status text", () => {
    expect(renderToStaticMarkup(<StatusBadge value="NON_COMPLIANT" />)).toContain("At Risk");
  });

  it("renders confirmation as a labeled modal dialog", () => {
    const markup = renderToStaticMarkup(
      <ConfirmDialog open title="Revoke worker" message="This cannot be undone." onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(markup).toContain('role="dialog"');
    expect(markup).toContain('aria-modal="true"');
    expect(markup).toContain("Revoke worker");
  });
});
