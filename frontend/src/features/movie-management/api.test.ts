import { describe, expect, it, vi } from "vitest";
import { createHttpClient } from "../../lib/http";
import { createMovieManagementApi, validatePoster } from "./api";
describe("poster boundary", () => {
  it("sends browser-managed multipart data without a JSON content type", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 200,
          message: "OK",
          data: {
            movieId: 1,
            posterUrl: "https://images.example.test/poster.png",
          },
        }),
      ),
    );
    const api = createMovieManagementApi(
      createHttpClient({
        baseUrl: "/api",
        accessToken: () => "token",
        fetcher,
      }),
    );
    const file = new File(["png-bytes"], "poster.png", { type: "image/png" });
    await api.uploadPoster(1, file);
    const request = fetcher.mock.calls[0][1]!;
    expect(request.method).toBe("PUT");
    expect(request.body).toBeInstanceOf(FormData);
    expect((request.body as FormData).get("file")).toBe(file);
    expect(request.headers).not.toHaveProperty("Content-Type");
    expect(request.headers).toMatchObject({ Authorization: "Bearer token" });
  });
  it("rejects unsupported, empty and oversized files before dispatch", () => {
    expect(() =>
      validatePoster(new File([], "empty.png", { type: "image/png" })),
    ).toThrow();
    expect(() =>
      validatePoster(
        new File(["image"], "poster.svg", { type: "image/svg+xml" }),
      ),
    ).toThrow();
    expect(() =>
      validatePoster(
        new File([new Uint8Array(5 * 1024 * 1024 + 1)], "large.png", {
          type: "image/png",
        }),
      ),
    ).toThrow();
  });
});
