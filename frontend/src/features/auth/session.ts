import { z } from "zod";
import { ApiError, type HttpClient } from "../../lib/http";

const loginSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number().positive(),
  user: z.object({
    userId: z.number().int().positive(),
    fullName: z.string(),
    roles: z.array(z.string()),
  }),
});
export type Session = {
  token: string;
  expiresAt: number;
  user: z.infer<typeof loginSchema>["user"];
} | null;

export function createSessionStore() {
  let current: Session = null;
  let version = 0;
  const listeners = new Set<() => void>();
  const publish = () => listeners.forEach((listener) => listener());
  return {
    getSnapshot: () => current,
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    clear: () => {
      version++;
      current = null;
      publish();
    },
    async login(
      client: HttpClient,
      email: string,
      password: string,
      signal?: AbortSignal,
    ) {
      const requestVersion = ++version;
      const result = await client.request("/auth/login", loginSchema, {
        method: "POST",
        body: { email, password },
        anonymous: true,
        signal,
      });
      if (requestVersion !== version) return;
      if (!result.user.roles.includes("ADMIN"))
        throw new ApiError(
          403,
          "Cần tài khoản quản trị để sử dụng màn hình này.",
        );
      current = {
        token: result.accessToken,
        expiresAt: Date.now() + result.expiresIn * 1000,
        user: result.user,
      };
      publish();
    },
  };
}
