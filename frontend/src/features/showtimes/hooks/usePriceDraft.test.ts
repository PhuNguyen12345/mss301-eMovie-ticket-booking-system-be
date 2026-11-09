import { describe, expect, it } from "vitest";
import { parsePriceDraft } from "./usePriceDraft";
describe("price draft validation", () => {
  it("keeps free tickets and unknown seat types, omitting only blank inputs", () => {
    expect(parsePriceDraft({ 1: "", 2: "0", 99: "123,45" })).toEqual([
      { seatTypeId: 2, price: 0 },
      { seatTypeId: 99, price: 123.45 },
    ]);
  });
  it.each(["-1", "1.001", "1e5", "NaN", "Infinity", "10000000000"])(
    "rejects invalid amount %s",
    (amount) => {
      expect(() => parsePriceDraft({ 1: amount })).toThrow();
    },
  );
  it("accepts the maximum database price and rejects non-positive types", () => {
    expect(parsePriceDraft({ 1: "9999999999.99" })[0].price).toBe(
      9999999999.99,
    );
    expect(() => parsePriceDraft({ 0: "10" })).toThrow();
  });
});
