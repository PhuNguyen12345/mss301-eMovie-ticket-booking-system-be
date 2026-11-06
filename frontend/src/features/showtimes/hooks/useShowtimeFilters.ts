import { useState } from "react";
import { addDays, localDate } from "../../../lib/date";
import type { ShowtimeFilters } from "../model/contracts";

export function useShowtimeFilters() {
  const [filters, setFilters] = useState<ShowtimeFilters>({
    date: localDate(),
    status: "SCHEDULED",
    page: 0,
    size: 10,
  });
  const [weekStart, setWeekStart] = useState(filters.date);
  function update(patch: Partial<ShowtimeFilters>) {
    setFilters((previous) => ({
      ...previous,
      ...patch,
      page: patch.page ?? 0,
    }));
  }
  function selectDate(date: string) {
    if (!date) return;
    update({ date });
    if (date < weekStart || date > addDays(weekStart, 6)) setWeekStart(date);
  }
  function moveWeek(offset: number) {
    const next = addDays(weekStart, offset * 7);
    setWeekStart(next);
    update({ date: next });
  }
  return { filters, weekStart, update, selectDate, moveWeek };
}
