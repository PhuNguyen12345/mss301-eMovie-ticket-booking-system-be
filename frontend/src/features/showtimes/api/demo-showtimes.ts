import {
  createShow,
  loadShows,
  movies,
  persist,
  seatTypes,
  validatePrices,
} from "./demo-fixtures";
import { hasStarted } from "../../../lib/date";
import { ApiError } from "../../../lib/http";
import type { ShowtimeRepository } from "../model/contracts";

export function createDemoShowtimes(): ShowtimeRepository {
  const find = (id: number) => {
    const shows = loadShows();
    const show = shows.find((s) => s.id === id);
    if (!show) throw new ApiError(404, "Suất chiếu không tồn tại.");
    return { show, shows };
  };
  return {
    mode: "demo",
    seatTypes: seatTypes.map(({ id, name }) => ({ id, name })),
    async list(filters, signal) {
      signal?.throwIfAborted();
      const data = loadShows()
        .filter(
          (s) =>
            s.startTime.startsWith(filters.date) &&
            (!filters.cinemaId || s.cinemaId === filters.cinemaId) &&
            (!filters.screenId || s.screenId === filters.screenId) &&
            (!filters.movieId || s.movieId === filters.movieId) &&
            (!filters.status || s.status === filters.status),
        )
        .sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id);
      const totalPages = Math.ceil(data.length / filters.size);
      return {
        data: data.slice(
          filters.page * filters.size,
          (filters.page + 1) * filters.size,
        ),
        page: filters.page,
        size: filters.size,
        totalElements: data.length,
        totalPages,
        first: filters.page === 0,
        last: filters.page >= totalPages - 1,
      };
    },
    async movies(signal) {
      signal?.throwIfAborted();
      return movies.map((m) => ({
        id: m.id,
        title: m.title,
        durationMinutes: m.duration,
        ageRating: m.age,
        status: "PRESENTING",
      }));
    },
    async prices(id, signal) {
      signal?.throwIfAborted();
      return find(id).show.prices;
    },
    async create(input) {
      const shows = loadShows();
      const created = createShow(shows, input);
      persist([...shows, created]);
      return created;
    },
    async replacePrices(id, prices) {
      validatePrices(prices);
      const { shows } = find(id);
      persist(shows.map((s) => (s.id === id ? { ...s, prices } : s)));
      return prices;
    },
    async update(id, input) {
      const { show, shows } = find(id);
      if (show.status !== "SCHEDULED" || hasStarted(show.startTime))
        throw new ApiError(
          409,
          "Suất chiếu đã hủy hoặc bắt đầu, không thể sửa.",
        );
      const updated = {
        ...createShow(
          shows.filter((s) => s.id !== id),
          input,
        ),
        id,
        prices: show.prices,
      };
      persist(shows.map((s) => (s.id === id ? updated : s)));
      return updated;
    },
    async cancel(id) {
      const { show, shows } = find(id);
      if (show.status !== "CANCELLED" && hasStarted(show.startTime))
        throw new ApiError(409, "Suất chiếu đã bắt đầu, không thể hủy.");
      const updated = { ...show, status: "CANCELLED" as const };
      persist(shows.map((s) => (s.id === id ? updated : s)));
      return updated;
    },
  };
}
