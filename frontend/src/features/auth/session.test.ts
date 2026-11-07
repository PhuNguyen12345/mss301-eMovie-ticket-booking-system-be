import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { createSessionStore } from "./session";
import { createHttpClient, type HttpClient } from "../../lib/http";
const credentials = (id = 1, token = "access-1", refresh = "refresh-1") => ({
  accessToken: token,
  tokenType: "Bearer",
  expiresIn: 100,
  refreshToken: refresh,
  refreshExpiresIn: 1000,
  user: { userId: id, fullName: `User ${id}`, roles: ["CUSTOMER"] },
});
const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify({ status, message: "OK", data }), { status });
function setup() {
  const store = createSessionStore();
  const fetcher = vi.fn<typeof fetch>();
  const client: HttpClient = createHttpClient({
    baseUrl: "/api",
    fetcher,
    accessToken: () => store.accessToken(client),
    onUnauthorized: (token) => {
      if (store.getSnapshot()?.token === token) store.clear();
    },
  });
  const login = async (id = 1) => {
    fetcher.mockResolvedValueOnce(response(credentials(id)));
    await store.login(client, "user@example.test", "password");
  };
  return { store, fetcher, client, login };
}
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2030-01-01T00:00:00Z"));
});
afterEach(() => vi.useRealTimers());
describe("in-memory session rotation", () => {
  it("shares one refresh between concurrent callers and preserves session identity", async () => {
    const { store, fetcher, client, login } = setup();
    await login();
    const owner = store.getSnapshot()!;
    expect(owner).not.toHaveProperty("refreshToken");
    vi.advanceTimersByTime(81000);
    let finish!: (value: Response) => void;
    fetcher.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const first = store.accessToken(client),
      second = store.accessToken(client);
    expect(fetcher).toHaveBeenCalledTimes(2);
    finish(response(credentials(1, "access-2", "refresh-2")));
    expect(await Promise.all([first, second])).toEqual([
      "access-2",
      "access-2",
    ]);
    expect(store.getSnapshot()?.sessionId).toBe(owner.sessionId);
    expect(JSON.parse(String(fetcher.mock.calls[1][1]?.body))).toEqual({
      refreshToken: "refresh-1",
    });
    expect(fetcher.mock.calls[1][1]?.headers).not.toHaveProperty(
      "Authorization",
    );
  });
  it("does not restore a session after logout during refresh", async () => {
    const { store, fetcher, client, login } = setup();
    await login();
    vi.advanceTimersByTime(81000);
    let finish!: (value: Response) => void;
    fetcher.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const pending = store.accessToken(client);
    store.clear();
    finish(response(credentials(1, "access-2")));
    await expect(pending).rejects.toMatchObject({ code: "SESSION_CHANGED" });
    expect(store.getSnapshot()).toBeNull();
  });
  it("does not overwrite another login with an earlier refresh result", async () => {
    const { store, fetcher, client, login } = setup();
    await login();
    vi.advanceTimersByTime(81000);
    let finish!: (value: Response) => void;
    fetcher.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const pending = store.accessToken(client);
    await login(2);
    finish(response(credentials(1, "old-user-token")));
    await expect(pending).rejects.toMatchObject({ code: "SESSION_CHANGED" });
    expect(store.getSnapshot()?.user.userId).toBe(2);
  });
  it("clears failed rotation without automatically replaying the credential", async () => {
    const { store, fetcher, client, login } = setup();
    await login();
    vi.advanceTimersByTime(81000);
    fetcher.mockResolvedValueOnce(response(null, 503));
    await expect(store.accessToken(client)).rejects.toMatchObject({
      status: 503,
    });
    expect(store.getSnapshot()).toBeNull();
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("does not clear a newer session when an old refresh fails", async () => {
    const { store, fetcher, client, login } = setup();
    await login();
    vi.advanceTimersByTime(81000);
    let finish!: (value: Response) => void;
    fetcher.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const pending = store.refresh(client);
    await login(2);
    finish(response(null, 401));
    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(store.getSnapshot()?.user.userId).toBe(2);
  });
  it("refreshes before a mutation and sends that mutation exactly once", async () => {
    const { store, fetcher, client, login } = setup();
    await login();
    vi.advanceTimersByTime(81000);
    fetcher
      .mockResolvedValueOnce(response(credentials(1, "renewed")))
      .mockResolvedValueOnce(response({ id: 12 }));
    await client.request("/bookings", z.object({ id: z.number() }), {
      method: "POST",
      body: { showtimeId: 1, seatIds: [1] },
    });
    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "/api/auth/login",
      "/api/auth/refresh",
      "/api/bookings",
    ]);
    expect(fetcher.mock.calls[2][1]?.headers).toMatchObject({
      Authorization: "Bearer renewed",
    });
    expect(store.getSnapshot()?.token).toBe("renewed");
  });
  it("does not dispatch an aborted operation after waiting for refresh", async () => {
    const { fetcher, client, login } = setup();
    await login();
    vi.advanceTimersByTime(81000);
    let finish!: (value: Response) => void;
    fetcher.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const abort = new AbortController();
    const pending = client.request("/bookings", z.unknown(), {
      method: "POST",
      body: {},
      signal: abort.signal,
    });
    abort.abort();
    finish(response(credentials(1, "renewed")));
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});
