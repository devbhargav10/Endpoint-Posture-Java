import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, clearToken, setToken } from "@/lib/api";
import { clearCurrentUser } from "@/lib/auth";

const backendUrl = process.env.BACKEND_API_URL;
const username = process.env.BACKEND_API_USERNAME ?? "admin";
const password = process.env.BACKEND_API_PASSWORD;
const nativeFetch = globalThis.fetch.bind(globalThis);
let session = new Map<string, string>();

afterEach(() => {
  clearToken();
  clearCurrentUser();
  vi.unstubAllGlobals();
});

describe.skipIf(!backendUrl || !password)("frontend API against the live backend", () => {
  beforeEach(() => {
    session = new Map<string, string>();
    const storage = {
      getItem: (key: string) => session.get(key) ?? null,
      setItem: (key: string, value: string) => session.set(key, value),
      removeItem: (key: string) => session.delete(key),
    };
    vi.stubGlobal("sessionStorage", storage);
    vi.stubGlobal("window", {
      sessionStorage: storage,
      location: { origin: backendUrl, href: "" },
    });
    vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) =>
      nativeFetch(new URL(String(input), `${backendUrl}/`), init),
    );
  });

  it("logs in, reads the dashboard, pages endpoints, and uses the admin route", async () => {
    const login = await api.login(username, password!);
    expect(login.username).toBe(username);
    expect(login.role).toBe("ADMIN");
    expect(typeof login.token).toBe("string");
    expect(login.token.length).toBeGreaterThan(20);
    setToken(login.token);

    const summary = await api.dashboardSummary();
    expect(summary).toEqual(expect.objectContaining({
      total: expect.any(Number),
      connected: expect.any(Number),
      notConnected: expect.any(Number),
      compliant: expect.any(Number),
      nonCompliant: expect.any(Number),
      error: expect.any(Number),
      unassessed: expect.any(Number),
      stale: expect.any(Number),
    }));

    const page = await api.endpointsPage({ page: 0, size: 25 });
    expect(page).toEqual(expect.objectContaining({
      items: expect.any(Array),
      total: expect.any(Number),
      page: 0,
      size: 25,
    }));

    const users = await api.users();
    expect(users.some((user) => user.username === username && user.role === "ADMIN")).toBe(true);
  });
});