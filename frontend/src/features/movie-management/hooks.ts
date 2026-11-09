import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices } from "../../app/ServicesProvider";
import { ApiError } from "../../lib/http";
import type {
  ReferenceKind,
  ReferenceSearch,
  ReferenceInput,
  CreditInput,
  Reference,
} from "./api";

export const managementKeys = {
  all: ["movie-management"] as const,
  references: (kind: ReferenceKind) => ["movie-management", kind] as const,
  genres: (id: number) => ["movie-management", "movie", id, "genres"] as const,
  credits: (id: number) =>
    ["movie-management", "movie", id, "credits"] as const,
};
export function useGenreOptions() {
  const { movieManagement, showtimes } = useServices();
  return useQuery({
    queryKey: [...managementKeys.references("genres"), "options"],
    enabled: showtimes.mode === "api",
    queryFn: async ({ signal }) => {
      const rows: Reference[] = [];
      for (let page = 0; page < 100; page++) {
        const result = await movieManagement.references(
          "genres",
          { keyword: "", page, size: 100 },
          signal,
        );
        if (result.page !== page || (!result.last && !result.data.length))
          throw new ApiError(
            200,
            "Phân trang thể loại không hợp lệ.",
            "INVALID_RESPONSE",
          );
        rows.push(...result.data);
        if (result.last) return rows;
      }
      throw new ApiError(
        200,
        "Danh mục thể loại vượt giới hạn tải.",
        "INVALID_RESPONSE",
      );
    },
  });
}
export function useReferences(kind: ReferenceKind, filters: ReferenceSearch) {
  const { movieManagement, showtimes } = useServices();
  return useQuery({
    queryKey: [...managementKeys.references(kind), filters],
    queryFn: ({ signal }) => movieManagement.references(kind, filters, signal),
    enabled: showtimes.mode === "api",
  });
}
export function useReferenceMutation(kind: ReferenceKind) {
  const { movieManagement } = useServices();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (
      command: { input: ReferenceInput; id?: number } | { deleteId: number },
    ) =>
      "deleteId" in command
        ? movieManagement.deleteReference(kind, command.deleteId)
        : movieManagement.saveReference(kind, command.input, command.id),
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: managementKeys.all }),
        cache.invalidateQueries({ queryKey: ["catalogue"] }),
      ]);
    },
  });
}
export function useMovieGenres(id: number) {
  const { movieManagement } = useServices();
  return useQuery({
    queryKey: managementKeys.genres(id),
    queryFn: ({ signal }) => movieManagement.genres(id, signal),
    enabled: Number.isSafeInteger(id) && id > 0,
  });
}
export function useMovieCredits(id: number) {
  const { movieManagement } = useServices();
  return useQuery({
    queryKey: managementKeys.credits(id),
    queryFn: ({ signal }) => movieManagement.credits(id, signal),
    enabled: Number.isSafeInteger(id) && id > 0,
  });
}
export function useMovieAssets(id: number) {
  const { movieManagement } = useServices();
  const cache = useQueryClient();
  const invalidate = async () => {
    await Promise.all([
      cache.invalidateQueries({ queryKey: ["catalogue"] }),
      cache.invalidateQueries({ queryKey: ["movies"] }),
      cache.invalidateQueries({ queryKey: ["movie-management", "movie", id] }),
    ]);
  };
  const genres = useMutation({
    mutationFn: (ids: number[]) => movieManagement.replaceGenres(id, ids),
    onSuccess: invalidate,
  });
  const credits = useMutation({
    mutationFn: (input: CreditInput[]) =>
      movieManagement.replaceCredits(id, input),
    onSuccess: invalidate,
  });
  const poster = useMutation({
    mutationFn: (input: File | string) =>
      typeof input === "string"
        ? movieManagement.importPoster(id, input)
        : movieManagement.uploadPoster(id, input),
    onSuccess: invalidate,
  });
  return { genres, credits, poster };
}
