import { describe, expect, it } from "vitest";
import { bookingIdFromOrderCode, paymentUrl } from "./api";
import { serverTimestamp, localDate } from "../../lib/date";
describe("payment boundary", () => {
  it("allows only HTTPS PayOS checkout URLs", () => {
    expect(paymentUrl("https://pay.payos.vn/web/test")).toBe(
      "https://pay.payos.vn/web/test",
    );
    for (const url of [
      null,
      "javascript:alert(1)",
      "https://payos.vn.attacker.example",
      "https://payos.vn@attacker.example",
      "http://pay.payos.vn/test",
      "https://user@pay.payos.vn/test",
    ])
      expect(() => paymentUrl(url)).toThrow();
  });
  it("uses orderCode for identification only, validating the backend numbering contract", () => {
    expect(bookingIdFromOrderCode("1201")).toBe(12);
    for (const code of [
      null,
      "100",
      "0",
      "-1",
      "1.01",
      "abc",
      "90071992547409911",
    ])
      expect(bookingIdFromOrderCode(code)).toBeNull();
  });
  it("interprets server local times in Vietnam without changing explicit offsets", () => {
    expect(serverTimestamp("2026-11-05T08:00:00")).toBe(
      Date.parse("2026-11-05T01:00:00Z"),
    );
    expect(serverTimestamp("2026-11-05T01:00:00Z")).toBe(
      Date.parse("2026-11-05T01:00:00Z"),
    );
    expect(localDate(new Date("2026-11-05T18:00:00Z"))).toBe("2026-11-06");
  });
});
