import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, request } from "./api";

afterEach(() => vi.unstubAllGlobals());

describe("central API request handling", () => {
  it("parses successful JSON responses and sets standard headers", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"ok":true}', { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(request<{ ok: boolean }>("/api/example")).resolves.toEqual({ ok: true });
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(new Headers(init.headers).get("Accept")).toBe("application/json");
  });

  it("retains server error status and message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response('{"message":"Forbidden by policy"}', { status: 403 })));
    await expect(request("/api/forbidden")).rejects.toMatchObject({
      name: "ApiError",
      status: 403,
      message: "Forbidden by policy",
    });
  });

  it("returns null for an empty successful response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(request("/api/empty")).resolves.toBeNull();
  });

  it("does not set JSON content type on multipart requests", async () => {
    const form = new FormData();
    form.append("field", "value");
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await request("/api/upload", { method: "POST", body: form });
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
  });

  it("uses a safe connection error for network failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("socket detail")));
    await expect(request("/api/offline")).rejects.toBeInstanceOf(ApiError);
    await expect(request("/api/offline")).rejects.toMatchObject({ status: 0 });
  });
});
