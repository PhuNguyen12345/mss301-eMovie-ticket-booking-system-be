import { z } from "zod";
import { ApiError, type HttpClient } from "../../lib/http";

const loginSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number().positive(),
  refreshToken: z.string().min(1).nullish(),
  refreshExpiresIn: z.number().positive().nullish(),
  user: z.object({
    userId: z.number().int().positive(),
    fullName: z.string(),
    roles: z.array(z.string()),
  }),
});
export type Session = {
  sessionId: number;
  token: string;
  expiresAt: number;
  refreshAt: number;
  user: z.infer<typeof loginSchema>["user"];
} | null;

export function createSessionStore() {
  let current: Session = null;
  let version = 0;
  let refreshToken: string | undefined;
  let refreshExpiresAt = 0;
  let inFlight: { version: number; promise: Promise<void> } | undefined;
  const listeners = new Set<() => void>();
  const publish = () => listeners.forEach((listener) => listener());
  const clear = () => {
    version++;
    current = null;
    refreshToken = undefined;
    refreshExpiresAt = 0;
    publish();
  };
  const accept = (
    result: z.infer<typeof loginSchema>,
    requestVersion: number,
  ) => {
    if (requestVersion !== version) return;
    refreshToken = result.refreshToken ?? undefined;
    refreshExpiresAt = Date.now() + (result.refreshExpiresIn ?? 0) * 1000;
    const expiresAt = Date.now() + result.expiresIn * 1000;
    current = {
      sessionId: requestVersion,
      token: result.accessToken,
      expiresAt,
      refreshAt: refreshToken
        ? expiresAt - Math.min(60000, result.expiresIn * 200)
        : expiresAt,
      user: result.user,
    };
    publish();
  };
  function refresh(client: HttpClient): Promise<void> {
    const owner = current;
    if (!owner) return Promise.resolve();
    if (inFlight?.version === owner.sessionId) return inFlight.promise;
    const credential = refreshToken;
    if (!credential || refreshExpiresAt <= Date.now()) {
      clear();
      return Promise.reject(
        new ApiError(
          401,
          "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        ),
      );
    }
    // Rotation is single-flight, never retried, and cannot revive a logged-out session.
    const promise = (async () => {
      try {
        const result = await client.request("/auth/refresh", loginSchema, {
          method: "POST",
          body: { refreshToken: credential },
          anonymous: true,
        });
        if (
          !result.refreshToken ||
          !result.refreshExpiresIn ||
          result.user.userId !== owner.user.userId
        ) {
          throw new ApiError(
            401,
            "Không thể gia hạn phiên. Vui lòng đăng nhập lại.",
          );
        }
        accept(result, owner.sessionId);
      } catch (error) {
        if (version === owner.sessionId) clear();
        throw error;
      }
    })().finally(() => {
      if (inFlight?.promise === promise) inFlight = undefined;
    });
    inFlight = { version: owner.sessionId, promise };
    return promise;
  }
  return {
    getSnapshot: () => current,
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    clear,
    refresh,
    async accessToken(client: HttpClient) {
      const owner = current;
      if (!owner) return undefined;
      if (Date.now() >= owner.refreshAt) await refresh(client);
      if (!current || current.sessionId !== owner.sessionId) {
        throw new ApiError(
          401,
          "Phiên đăng nhập đã thay đổi. Vui lòng thử lại.",
          "SESSION_CHANGED",
        );
      }
      return current.token;
    },
    async login(
      client: HttpClient,
      email: string,
      password: string,
      signal?: AbortSignal,
    ) {
      clear();
      const requestVersion = version;
      const result = await client.request("/auth/login", loginSchema, {
        method: "POST",
        body: { email, password },
        anonymous: true,
        signal,
      });
      if (!signal?.aborted) accept(result, requestVersion);
    },
  };
}
