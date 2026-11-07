import { z } from "zod";
import type { HttpClient } from "../../lib/http";
import { pageSchema } from "../showtimes/model/contracts";
import { creditSchema } from "../movie-management/api";

export const movieSchema = z.object({
  id: z.number().int().positive(),
  title: z.string().min(1),
  description: z.string().nullish(),
  durationMinutes: z.number().int().positive(),
  releaseDate: z.string().nullish(),
  language: z.string().nullish(),
  ageRating: z.string().nullish(),
  posterUrl: z.string().nullish(),
  trailerUrl: z.string().nullish(),
  status: z.enum(["WILL_PRESENT", "PRESENTING", "EXPIRED"]),
  genres: z.array(z.object({ id: z.number(), name: z.string() })).optional(),
  credits: z.array(creditSchema).optional(),
});
export type Movie = z.infer<typeof movieSchema>;
export type MovieInput = Omit<Movie, "id" | "genres" | "credits">;
export type MovieSearch = {
  keyword: string;
  status: string;
  genreId?: number;
  page: number;
  size: number;
};
export function createMovieApi(client: HttpClient) {
  return {
    list: (filters: MovieSearch, signal?: AbortSignal) => {
      const params = new URLSearchParams();
      Object.entries(filters).forEach(([key, value]) => {
        if (value !== undefined && value !== "") params.set(key, String(value));
      });
      return client.request(`/movies?${params}`, pageSchema(movieSchema), {
        signal,
      });
    },
    detail: (id: number, signal?: AbortSignal) =>
      client.request(`/movies/${id}`, movieSchema, { signal }),
    save: (input: MovieInput, id?: number) =>
      client.request(id ? `/movies/${id}` : "/movies", movieSchema, {
        method: id ? "PUT" : "POST",
        body: input,
      }),
    status: (id: number, status: Movie["status"]) =>
      client.request(`/movies/${id}/status`, movieSchema, {
        method: "PATCH",
        body: { status },
      }),
  };
}
