// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import AuditPage from "./page";

const { auditActionsPage, auditSummary } = vi.hoisted(() => ({
  auditActionsPage: vi.fn(),
  auditSummary: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  api: { auditActionsPage, auditSummary },
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: unknown; [key: string]: unknown }) =>
    <a href={href} {...props}>{children as never}</a>,
}));

vi.mock("@/components/ui/DataTable", () => ({
  DataTable: ({ rows, toolbarLeft }: { rows: { id: string }[] | null; toolbarLeft?: string }) => (
    <section>
      <div>{toolbarLeft}</div>
      {rows?.map((row) => <div key={row.id} data-testid="audit-row">{row.id}</div>)}
    </section>
  ),
}));

const page = (items: { id: string }[], pageNo: number, total: number) => ({
  items: items.map((item) => ({
    id: item.id,
    endpointId: "endpoint-1",
    actionType: "RESTRICT" as const,
    operator: "admin",
    succeeded: false,
    detail: "failure",
    occurredAt: "2026-11-18T00:00:00Z",
  })),
  total,
  page: pageNo,
  size: 25,
});

beforeEach(() => {
  auditActionsPage.mockReset();
  auditSummary.mockReset().mockResolvedValue({ total: 50, sharePosture: 10, restrict: 20, succeeded: 40 });
  auditActionsPage.mockResolvedValue(page([{ id: "row-a" }], 0, 2));
});

afterEach(() => {
  cleanup();
});

describe("audit pagination interactions", () => {
  it("loads the next page and does not append duplicate audit rows", async () => {
    auditActionsPage.mockImplementation(({ page: pageNo }: { page: number }) =>
      Promise.resolve(pageNo === 0 ? page([{ id: "row-a" }], 0, 2) : page([{ id: "row-a" }, { id: "row-b" }], 1, 3)),
    );
    render(<AuditPage />);

    await screen.findByText("row-a");
    fireEvent.click(screen.getByRole("button", { name: "Load older actions" }));
    await screen.findByText("row-b");

    expect(auditActionsPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 25 }));
    expect(screen.getAllByTestId("audit-row").map((row) => row.textContent)).toEqual(["row-a", "row-b"]);
  });

  it("keeps action, result and text filters in the page request", async () => {
    auditActionsPage.mockResolvedValue(page([], 0, 0));
    render(<AuditPage />);
    await waitFor(() => expect(auditActionsPage).toHaveBeenCalled());

    const filters = screen.getAllByRole("combobox");
    fireEvent.change(filters[0], { target: { value: "RESTRICT" } });
    fireEvent.change(filters[1], { target: { value: "SUCCESS" } });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "quarantine" } });

    await waitFor(() => expect(auditActionsPage).toHaveBeenLastCalledWith({
      page: 0,
      size: 25,
      actionType: "RESTRICT",
      succeeded: true,
      q: "quarantine",
    }), { timeout: 2500 });
  });

  it("shows an error and retries the failed page request", async () => {
    auditActionsPage.mockRejectedValueOnce(new Error("service unavailable"))
      .mockResolvedValueOnce(page([], 0, 0));
    render(<AuditPage />);
    expect((await screen.findByRole("alert")).textContent).toContain("service unavailable");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(auditActionsPage).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  });
});
