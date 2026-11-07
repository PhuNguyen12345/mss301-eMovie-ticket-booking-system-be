import { useQuery } from "@tanstack/react-query";
import { useServices } from "../../app/ServicesProvider";
import type { Movie, MovieSearch } from "./api";
import { ApiError } from "../../lib/http";

export const movieKeys = {
  all: ["catalogue"] as const,
  list: (filters: MovieSearch) => ["catalogue", "list", filters] as const,
  detail: (id: number) => ["catalogue", "detail", id] as const,
};
export function useMovieCatalogue(filters: MovieSearch) {
  const { movies, showtimes } = useServices();
  return useQuery({
    queryKey: movieKeys.list(filters),
    queryFn: async ({ signal }) => {
      if (showtimes.mode === "api") return movies.list(filters, signal);
      const rows: Movie[] = (await showtimes.movies(signal)).map((m) => ({
        ...m,
        status: "PRESENTING",
      }));
      const matches = rows.filter(
        (m) =>
          (!filters.status || m.status === filters.status) &&
          m.title
            .toLocaleLowerCase("vi")
            .includes(filters.keyword.toLocaleLowerCase("vi")),
      );
      return {
        data: matches.slice(
          filters.page * filters.size,
          (filters.page + 1) * filters.size,
        ),
        page: filters.page,
        size: filters.size,
        totalElements: matches.length,
        totalPages: Math.ceil(matches.length / filters.size),
        first: filters.page === 0,
        last: (filters.page + 1) * filters.size >= matches.length,
      };
    },
  });
}
export function useMovie(id: number) {
  const { movies, showtimes } = useServices();
  return useQuery({
    queryKey: movieKeys.detail(id),
    enabled: Number.isSafeInteger(id) && id > 0,
    queryFn: async ({ signal }) => {
      if (showtimes.mode === "api") return movies.detail(id, signal);
      const found = (await showtimes.movies(signal)).find((m) => m.id === id);
      if (!found) throw new ApiError(404, "Không tìm thấy phim.");
      return { ...found, status: "PRESENTING" } as Movie;
    },
  });
}
