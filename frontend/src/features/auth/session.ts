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

type SessionOptions = {
  cookie?: boolean;
  exclusive?: <T>(operation: () => Promise<T>) => Promise<T>;
};
export function createSessionStore(options: SessionOptions = {}) {
  let queue = Promise.resolve();
  const exclusive = <T>(operation: () => Promise<T>): Promise<T> => {
    if (!options.cookie) return operation();
    const result = queue.then(() =>
      options.exclusive ? options.exclusive(operation) : operation(),
    );
    queue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  };
  let current: Session = null;
  let version = 0;
  let refreshToken: string | undefined;
  let refreshExpiresAt = 0;
  let inFlight: { version: number; promise: Promise<void> } | undefined;
  let restoring: { version: number; promise: Promise<void> } | undefined;
  let lifecycle = {
    pending: !!options.cookie,
    error: null as unknown,
    loggingOut: false,
    logoutError: null as unknown,
  };
  let refreshError: unknown;
  const listeners = new Set<() => void>();
  const publish = () => listeners.forEach((listener) => listener());
  const clear = () => {
    version++;
    current = null;
    refreshToken = undefined;
    refreshExpiresAt = 0;
    lifecycle = {
      pending: false,
      error: null,
      loggingOut: false,
      logoutError: null,
    };
    refreshError = undefined;
    publish();
  };
  const accept = (
    result: z.infer<typeof loginSchema>,
    requestVersion: number,
  ) => {
    if (requestVersion !== version) return;
    refreshToken = options.cookie
      ? undefined
      : (result.refreshToken ?? undefined);
    refreshExpiresAt = Date.now() + (result.refreshExpiresIn ?? 0) * 1000;
    const expiresAt = Date.now() + result.expiresIn * 1000;
    current = {
      sessionId: requestVersion,
      token: result.accessToken,
      expiresAt,
      refreshAt:
        (options.cookie && result.refreshExpiresIn) || refreshToken
          ? expiresAt - Math.min(60000, result.expiresIn * 200)
          : expiresAt,
      user: result.user,
    };
    lifecycle = { ...lifecycle, pending: false, error: null };
    refreshError = undefined;
    publish();
  };
  function refresh(client: HttpClient): Promise<void> {
    const owner = current;
    if (!owner) return Promise.resolve();
    if (inFlight?.version === owner.sessionId) return inFlight.promise;
    const credential = refreshToken;
    if ((!options.cookie && !credential) || refreshExpiresAt <= Date.now()) {
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
        const result = await exclusive(() => {
          if (version !== owner.sessionId)
            throw new ApiError(
              401,
              "Phiên đăng nhập đã thay đổi.",
              "SESSION_CHANGED",
            );
          return client.request(
            options.cookie ? "/auth/refresh?transport=cookie" : "/auth/refresh",
            loginSchema,
            {
              method: "POST",
              body: options.cookie ? {} : { refreshToken: credential },
              anonymous: true,
              cookieSession: options.cookie,
            },
          );
        });
        if (
          (!options.cookie && !result.refreshToken) ||
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
        if (version === owner.sessionId) {
          if (
            options.cookie &&
            error instanceof ApiError &&
            (error.status === 0 || error.status >= 500)
          ) {
            refreshError = error;
            current = { ...owner, refreshAt: Date.now() + 10000 };
            publish();
          } else clear();
        }
        throw error;
      }
    })().finally(() => {
      if (inFlight?.promise === promise) inFlight = undefined;
    });
    inFlight = { version: owner.sessionId, promise };
    return promise;
  }
  function restore(client: HttpClient, retry = false): Promise<void> {
    if (!options.cookie || (current && !retry)) return Promise.resolve();
    if (restoring?.version === version) return restoring.promise;
    if (!lifecycle.pending && !retry) return Promise.resolve();
    const requestVersion = ++version;
    lifecycle = { ...lifecycle, pending: true, error: null };
    publish();
    const promise = exclusive(async () => {
      if (requestVersion !== version) return;
      try {
        const result = await client.request(
          "/auth/refresh?transport=cookie",
          loginSchema,
          {
            method: "POST",
            body: {},
            anonymous: true,
            cookieSession: true,
          },
        );
        accept(result, requestVersion);
      } catch (error) {
        if (requestVersion !== version) return;
        if (error instanceof ApiError && error.status === 401) clear();
        else {
          lifecycle = { ...lifecycle, pending: false, error };
          publish();
        }
      }
    }).finally(() => {
      if (restoring?.promise === promise) restoring = undefined;
    });
    restoring = { version: requestVersion, promise };
    return promise;
  }
  return {
    getSnapshot: () => current,
    getLifecycle: () => lifecycle,
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    clear,
    refresh,
    restore,
    async logout(client: HttpClient) {
      if (!options.cookie) {
        clear();
        return;
      }
      if (lifecycle.loggingOut) return;
      lifecycle = { ...lifecycle, loggingOut: true, logoutError: null };
      publish();
      try {
        await exclusive(() =>
          client.request("/auth/logout", z.null(), {
            method: "POST",
            body: {},
            anonymous: true,
            cookieSession: true,
          }),
        );
        clear();
      } catch (error) {
        lifecycle = { ...lifecycle, loggingOut: false, logoutError: error };
        publish();
        throw error;
      }
    },
    async accessToken(client: HttpClient) {
      if (lifecycle.loggingOut)
        throw new ApiError(409, "Đang đăng xuất. Vui lòng đợi.");
      if (lifecycle.pending) await restore(client);
      if (lifecycle.error) throw lifecycle.error;
      if (lifecycle.loggingOut)
        throw new ApiError(409, "Đang đăng xuất. Vui lòng đợi.");
      const owner = current;
      if (!owner) return undefined;
      if (
        refreshError &&
        Date.now() >= owner.expiresAt &&
        Date.now() < owner.refreshAt
      )
        throw refreshError;
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
      const result = await exclusive(() =>
        client.request(
          options.cookie ? "/auth/login?transport=cookie" : "/auth/login",
          loginSchema,
          {
            method: "POST",
            body: { email, password },
            anonymous: true,
            signal,
            cookieSession: options.cookie,
          },
        ),
      );
      if (!signal?.aborted) accept(result, requestVersion);
    },
  };
}
