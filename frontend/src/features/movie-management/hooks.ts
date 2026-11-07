import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices } from "../../app/ServicesProvider";
import type {
  ReferenceKind,
  ReferenceSearch,
  ReferenceInput,
  CreditInput,
} from "./api";

export const managementKeys = {
  all: ["movie-management"] as const,
  references: (kind: ReferenceKind) => ["movie-management", kind] as const,
  genres: (id: number) => ["movie-management", "movie", id, "genres"] as const,
  credits: (id: number) =>
    ["movie-management", "movie", id, "credits"] as const,
};
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
