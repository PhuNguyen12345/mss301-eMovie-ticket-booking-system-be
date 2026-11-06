import { z } from "zod";
import { ApiError, type HttpClient } from "../../../lib/http";
import {
  movieSchema,
  pageSchema,
  seatPriceSchema,
  showtimeSchema,
  type ShowtimeRepository,
} from "../model/contracts";

export function createHttpShowtimes(client: HttpClient): ShowtimeRepository {
  return {
    mode: "api",
    seatTypes: [],
    list(filters, signal) {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(filters))
        if (value !== undefined && value !== "") params.set(key, String(value));
      return client.request(
        `/showtimes?${params}`,
        pageSchema(showtimeSchema),
        { signal },
      );
    },
    async movies(signal) {
      const result = [];
      // Follow the server's pagination; never treat the first page as the entire catalogue.
      for (let page = 0; ; page++) {
        const batch = await client.request(
          `/movies?page=${page}&size=100`,
          pageSchema(movieSchema),
          { signal },
        );
        if (batch.page !== page || (!batch.last && !batch.data.length))
          throw new ApiError(
            200,
            "Phân trang danh mục phim không hợp lệ.",
            "INVALID_RESPONSE",
          );
        result.push(...batch.data);
        if (batch.last) return result;
        if (page >= 999)
          throw new ApiError(
            200,
            "Danh mục phim vượt giới hạn tải.",
            "INVALID_RESPONSE",
          );
      }
    },
    prices: (id, signal) =>
      client.request(`/showtimes/${id}/seat-prices`, z.array(seatPriceSchema), {
        signal,
      }),
    create: (body) =>
      client.request("/showtimes", showtimeSchema, { method: "POST", body }),
    replacePrices: (id, body) =>
      client.request(`/showtimes/${id}/seat-prices`, z.array(seatPriceSchema), {
        method: "PUT",
        body,
      }),
    cancel: (id) =>
      client.request(`/showtimes/${id}/cancel`, showtimeSchema, {
        method: "POST",
      }),
  };
}
