import { z } from "zod";
import { translateError } from "./errorMessages";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code = "HTTP_ERROR",
    /** Details the server sent with the error, such as check-in opening hours. */
    public readonly data?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
const envelope = z.object({
  status: z.number(),
  message: z.string(),
  data: z.unknown().optional(),
  /** Stable reason of an error, sent by booking-service for clients to show their own text. */
  code: z.string().optional(),
});
export const errorMessage = (error: unknown) =>
  error instanceof ApiError
    ? (translateError(error.code, error.data) ?? error.message)
    : error instanceof Error
      ? error.message
      : "Không thể hoàn tất thao tác. Vui lòng thử lại.";
export const shouldRetryQuery = (count: number, error: Error) =>
  count < 2 &&
  error.name !== "AbortError" &&
  (!(error instanceof ApiError) ||
    (error.code !== "INVALID_RESPONSE" &&
      (error.status === 0 || error.status >= 500)));

type ClientOptions = {
  baseUrl: string;
  accessToken?: () => string | undefined | Promise<string | undefined>;
  onUnauthorized?: (token: string) => void;
  fetcher?: typeof fetch;
  timeoutMs?: number;
};
type RequestOptions = {
  responseType?: "png";
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
  anonymous?: boolean;
  cookieSession?: boolean;
  timeoutMs?: number;
};

export function createHttpClient(options: ClientOptions) {
  const fetcher = options.fetcher ?? fetch;
  return {
    async request<T>(
      path: string,
      schema: z.ZodType<T>,
      request: RequestOptions = {},
    ): Promise<T> {
      const token = request.anonymous
        ? undefined
        : await options.accessToken?.();
      request.signal?.throwIfAborted();
      const timeout = AbortSignal.timeout(
        request.timeoutMs ?? options.timeoutMs ?? 10000,
      );
      const signal = request.signal
        ? AbortSignal.any([request.signal, timeout])
        : timeout;
      let response: Response;
      try {
        response = await fetcher(
          `${options.baseUrl.replace(/\/$/, "")}${path}`,
          {
            method: request.method ?? "GET",
            signal,
            credentials: request.cookieSession ? "include" : "omit",
            headers: {
              Accept:
                request.responseType === "png"
                  ? "image/png"
                  : "application/json",
              ...(request.body !== undefined &&
              !(request.body instanceof FormData)
                ? { "Content-Type": "application/json" }
                : {}),
              ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body:
              request.body instanceof FormData
                ? request.body
                : request.body === undefined
                  ? undefined
                  : JSON.stringify(request.body),
          },
        );
      } catch (error) {
        if (request.signal?.aborted) throw error;
        throw new ApiError(
          0,
          timeout.aborted
            ? "Kết nối quá thời gian chờ. Vui lòng thử lại."
            : "Không kết nối được máy chủ. Kiểm tra kết nối và thử lại.",
          "NETWORK_ERROR",
        );
      }
      if (response.status === 401 && token) options.onUnauthorized?.(token);
      if (response.ok && request.responseType === "png") {
        if (
          response.headers.get("content-type")?.split(";")[0].trim() !==
          "image/png"
        )
          throw new ApiError(
            response.status,
            "Ảnh vé trả về không hợp lệ.",
            "INVALID_RESPONSE",
          );
        try {
          const blob = await response.blob();
          if (!blob.size) throw new Error("Empty image");
          return schema.parse(blob);
        } catch (error) {
          if (request.signal?.aborted) throw error;
          throw new ApiError(
            response.status,
            "Không tải được ảnh vé. Vui lòng thử lại.",
            "INVALID_RESPONSE",
          );
        }
      }
      let payload: unknown;
      try {
        payload = await response.json();
      } catch (error) {
        if (request.signal?.aborted) throw error;
        if (timeout.aborted)
          throw new ApiError(
            0,
            "Kết nối quá thời gian chờ. Vui lòng thử lại.",
            "NETWORK_ERROR",
          );
        if (response.ok)
          throw new ApiError(
            response.status,
            "Máy chủ trả về dữ liệu không hợp lệ.",
            "INVALID_RESPONSE",
          );
      }
      const parsed = envelope.safeParse(payload);
      if (!response.ok) {
        const message =
          response.status === 401
            ? request.anonymous && path.split("?")[0] === "/auth/login"
              ? "Email hoặc mật khẩu không đúng."
              : "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            : response.status === 403
              ? "Tài khoản không có quyền thực hiện thao tác này."
              : response.status >= 500
                ? "Dịch vụ đang gặp sự cố. Vui lòng thử lại sau."
                : parsed.success
                  ? parsed.data.message
                  : "Yêu cầu không thành công.";
        // A server code is kept only where this app writes the text itself (4xx below 500, except 401/403).
        const code =
          parsed.success &&
          parsed.data.code &&
          response.status !== 401 &&
          response.status !== 403 &&
          response.status < 500
            ? parsed.data.code
            : undefined;
        throw new ApiError(
          response.status,
          message,
          code,
          parsed.success ? parsed.data.data : undefined,
        );
      }
      if (!parsed.success || parsed.data.status !== response.status)
        throw new ApiError(
          response.status,
          "Phản hồi không đúng định dạng.",
          "INVALID_RESPONSE",
        );
      const data = schema.safeParse(parsed.data.data);
      if (!data.success)
        throw new ApiError(
          response.status,
          "Dữ liệu trả về không đúng định dạng.",
          "INVALID_RESPONSE",
        );
      return data.data;
    },
  };
}
export type HttpClient = ReturnType<typeof createHttpClient>;
