import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { ApiError, createHttpClient, shouldRetryQuery } from "./http";

const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify({ status, message: "OK", data }), { status });
describe("HTTP boundary", () => {
  it("validates envelopes and forwards auth, request body and cancellation", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(response({ id: 7 }));
    const controller = new AbortController();
    const client = createHttpClient({
      baseUrl: "/api/",
      accessToken: () => "test-token",
      fetcher,
    });
    await expect(
      client.request("/showtimes/7", z.object({ id: z.number() }), {
        method: "PUT",
        body: { price: 90 },
        signal: controller.signal,
      }),
    ).resolves.toEqual({ id: 7 });
    expect(fetcher).toHaveBeenCalledWith(
      "/api/showtimes/7",
      expect.objectContaining({
        method: "PUT",
        body: '{"price":90}',
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
        }),
        signal: expect.any(AbortSignal),
      }),
    );
    controller.abort();
    expect((fetcher.mock.calls[0][1]?.signal as AbortSignal).aborted).toBe(
      true,
    );
  });
  it("rejects malformed successful responses without retrying them", async () => {
    const client = createHttpClient({
      baseUrl: "/api",
      fetcher: vi
        .fn<typeof fetch>()
        .mockResolvedValue(response({ id: "wrong" })),
    });
    await expect(
      client.request("/showtimes/7", z.object({ id: z.number() })),
    ).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
    expect(
      shouldRetryQuery(0, new ApiError(200, "Invalid", "INVALID_RESPONSE")),
    ).toBe(false);
    expect(shouldRetryQuery(0, new ApiError(503, "Unavailable"))).toBe(true);
    expect(shouldRetryQuery(2, new ApiError(503, "Unavailable"))).toBe(false);
    expect(shouldRetryQuery(0, new ApiError(404, "Missing"))).toBe(false);
  });
  it("clears an expired session once without replaying a mutation", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(response(null, 401));
    const onUnauthorized = vi.fn();
    const client = createHttpClient({
      baseUrl: "/api",
      fetcher,
      accessToken: () => "expired",
      onUnauthorized,
    });
    await expect(
      client.request("/showtimes", z.unknown(), { method: "POST", body: {} }),
    ).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledWith("expired");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("does not send stored credentials on login", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(response({ ok: true }));
    const client = createHttpClient({
      baseUrl: "/api",
      fetcher,
      accessToken: () => "old",
    });
    await client.request("/auth/login", z.unknown(), {
      method: "POST",
      anonymous: true,
      body: {},
    });
    expect(fetcher.mock.calls[0][1]?.headers).not.toHaveProperty(
      "Authorization",
    );
  });
  it("keeps caller aborts distinct from connection errors", async () => {
    const controller = new AbortController();
    controller.abort();
    const abort = new DOMException("Aborted", "AbortError");
    const client = createHttpClient({
      baseUrl: "/api",
      fetcher: vi.fn<typeof fetch>().mockRejectedValue(abort),
    });
    await expect(
      client.request("/showtimes", z.unknown(), { signal: controller.signal }),
    ).rejects.toBe(controller.signal.reason);
    expect(shouldRetryQuery(0, abort)).toBe(false);
  });
  it("preserves HTTP failure status when a proxy returns HTML", async () => {
    const client = createHttpClient({
      baseUrl: "/api",
      fetcher: vi
        .fn<typeof fetch>()
        .mockResolvedValue(
          new Response("<h1>Bad Gateway</h1>", { status: 502 }),
        ),
    });
    await expect(
      client.request("/showtimes", z.unknown()),
    ).rejects.toMatchObject({
      status: 502,
      code: "HTTP_ERROR",
    });
  });
  it("reports rejected login credentials without clearing another session", async () => {
    const onUnauthorized = vi.fn();
    const client = createHttpClient({
      baseUrl: "/api",
      accessToken: () => "existing-token",
      onUnauthorized,
      fetcher: vi.fn<typeof fetch>().mockResolvedValue(response(null, 401)),
    });
    await expect(
      client.request("/auth/login", z.unknown(), {
        method: "POST",
        anonymous: true,
        body: {},
      }),
    ).rejects.toMatchObject({
      status: 401,
      message: "Email hoặc mật khẩu không đúng.",
    });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
