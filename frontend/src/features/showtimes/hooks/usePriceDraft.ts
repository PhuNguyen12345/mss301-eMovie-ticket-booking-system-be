import { useEffect, useMemo, useState } from "react";
import type { PriceInput, SeatPrice, SeatType } from "../model/contracts";

type Values = Record<number, string>;
const signature = (prices: SeatPrice[]) =>
  JSON.stringify(
    [...prices]
      .sort((a, b) => a.seatTypeId - b.seatTypeId)
      .map((p) => [p.seatTypeId, p.price]),
  );
const toValues = (prices: SeatPrice[], types: SeatType[]): Values => ({
  ...Object.fromEntries(types.map((t) => [t.id, ""])),
  ...Object.fromEntries(prices.map((p) => [p.seatTypeId, String(p.price)])),
});
const normalized = (values: Values) =>
  JSON.stringify(
    Object.entries(values)
      .filter(([, value]) => value.trim() !== "")
      .sort(([a], [b]) => Number(a) - Number(b)),
  );
export function parsePriceDraft(values: Values): PriceInput[] {
  return Object.entries(values)
    .filter(([, value]) => value.trim() !== "")
    .map(([id, value]) => {
      const text = value.trim().replace(",", ".");
      const price = Number(text);
      if (!Number.isSafeInteger(Number(id)) || Number(id) <= 0)
        throw new Error("Mã loại ghế phải là số nguyên dương.");
      if (
        !/^\d+(?:\.\d{1,2})?$/.test(text) ||
        !Number.isFinite(price) ||
        price > 9999999999.99
      )
        throw new Error(
          "Giá phải từ 0 đến 9.999.999.999,99 đ, tối đa 2 chữ số thập phân.",
        );
      return { seatTypeId: Number(id), price };
    });
}
export function usePriceDraft(prices: SeatPrice[], types: SeatType[]) {
  const [baseline, setBaseline] = useState(() => ({
    signature: signature(prices),
    values: toValues(prices, types),
  }));
  const [values, setValues] = useState<Values>(baseline.values);
  const dirty = normalized(values) !== normalized(baseline.values);
  const remoteSignature = signature(prices);
  const remoteChanged = remoteSignature !== baseline.signature;
  const fields = useMemo(
    () =>
      Object.keys(values)
        .map(Number)
        .sort((a, b) => a - b),
    [values],
  );
  function reset(next: SeatPrice[] = prices) {
    const fresh = toValues(next, types);
    setValues(fresh);
    setBaseline({ signature: signature(next), values: fresh });
  }
  useEffect(() => {
    if (!dirty && remoteChanged) reset(prices);
  }, [dirty, remoteSignature]);
  return {
    values,
    fields,
    dirty,
    remoteChanged,
    reset,
    change: (id: number, value: string) =>
      setValues((previous) => ({ ...previous, [id]: value })),
    clear: () =>
      setValues((previous) =>
        Object.fromEntries(Object.keys(previous).map((id) => [id, ""])),
      ),
    add: (id: number) => {
      if (!Number.isSafeInteger(id) || id <= 0 || id in values)
        throw new Error(
          "Mã loại ghế phải là số nguyên dương và chưa có trong bảng.",
        );
      setValues((previous) => ({ ...previous, [id]: "" }));
    },
    submit: () => parsePriceDraft(values),
  };
}
