import { describe, expect, it, vi } from "vitest";
import { createHttpClient } from "../../lib/http";
import { createAdminApi, customerIds, MAX_CUSTOMER_LOOKUP } from "./api";

const envelope = (data: unknown) =>
  new Response(JSON.stringify({ status: 200, message: "OK", data }), {
    headers: { "Content-Type": "application/json" },
  });

describe("customer lookup for staff", () => {
  it("asks once for distinct valid ids and stays within the server limit", () => {
    expect(customerIds([74, 73, 74, 0, -1, 1.5, 73])).toEqual([73, 74]);
    const many = Array.from({ length: 150 }, (_, i) => i + 1);
    expect(customerIds(many)).toHaveLength(MAX_CUSTOMER_LOOKUP);
  });

  it("sends the ids with authorization and keeps only the summary fields", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(
      envelope([
        {
          userId: 73,
          fullName: "Nguyễn Văn An",
          email: "an.nguyen@example.com",
          phone: "0901234561",
          status: "ACTIVE",
          passwordHash: "should not matter",
        },
      ]),
    );
    const api = createAdminApi(
      createHttpClient({
        baseUrl: "/api",
        accessToken: () => "staff-token",
        fetcher,
      }),
    );
    const result = await api.customers([74, 73, 73]);
    expect(String(fetcher.mock.calls[0][0])).toContain("/api/users?ids=73,74");
    expect(fetcher.mock.calls[0][1]?.headers).toMatchObject({
      Authorization: "Bearer staff-token",
    });
    expect(result).toEqual([
      {
        userId: 73,
        fullName: "Nguyễn Văn An",
        email: "an.nguyen@example.com",
        phone: "0901234561",
        status: "ACTIVE",
      },
    ]);
  });

  it("rejects a malformed customer list", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(envelope([{ userId: "73", fullName: "X" }]));
    const api = createAdminApi(
      createHttpClient({ baseUrl: "/api", accessToken: () => "t", fetcher }),
    );
    await expect(api.customers([73])).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
  });
});
