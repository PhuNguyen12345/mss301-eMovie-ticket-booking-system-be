import { z } from "zod";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code = "HTTP_ERROR",
  ) {
    super(message);
    this.name = "ApiError";
  }
}
const envelope = z.object({
  status: z.number(),
  message: z.string(),
  data: z.unknown().optional(),
});
export const errorMessage = (error: unknown) =>
  error instanceof Error
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
  accessToken?: () => string | undefined;
  onUnauthorized?: (token: string) => void;
  fetcher?: typeof fetch;
  timeoutMs?: number;
};
type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
  anonymous?: boolean;
};

export function createHttpClient(options: ClientOptions) {
  const fetcher = options.fetcher ?? fetch;
  return {
    async request<T>(
      path: string,
      schema: z.ZodType<T>,
      request: RequestOptions = {},
    ): Promise<T> {
      const token = request.anonymous ? undefined : options.accessToken?.();
      const timeout = AbortSignal.timeout(options.timeoutMs ?? 10000);
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
            credentials: "omit",
            headers: {
              Accept: "application/json",
              ...(request.body !== undefined
                ? { "Content-Type": "application/json" }
                : {}),
              ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body:
              request.body === undefined
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
            ? request.anonymous
              ? "Email hoặc mật khẩu không đúng."
              : "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            : response.status === 403
              ? "Tài khoản không có quyền thực hiện thao tác này."
              : response.status >= 500
                ? "Dịch vụ đang gặp sự cố. Vui lòng thử lại sau."
                : parsed.success
                  ? parsed.data.message
                  : "Yêu cầu không thành công.";
        throw new ApiError(response.status, message);
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
