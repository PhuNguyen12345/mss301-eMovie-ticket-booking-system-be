import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices } from "../../../app/ServicesProvider";
import type {
  CreateShowtime,
  PriceInput,
  ShowtimeFilters,
} from "../model/contracts";

export const showtimeKeys = {
  lists: ["showtimes", "list"] as const,
  list: (filters: ShowtimeFilters) => ["showtimes", "list", filters] as const,
  prices: (id: number | undefined) => ["showtimes", "prices", id] as const,
  movies: ["movies"] as const,
};
export function useShowtimes(filters: ShowtimeFilters) {
  const { showtimes } = useServices();
  return useQuery({
    queryKey: showtimeKeys.list(filters),
    queryFn: ({ signal }) => showtimes.list(filters, signal),
  });
}
export function useMovies() {
  const { showtimes } = useServices();
  return useQuery({
    queryKey: showtimeKeys.movies,
    queryFn: ({ signal }) => showtimes.movies(signal),
    staleTime: 300000,
  });
}
export function useSeatPrices(id: number | undefined) {
  const { showtimes } = useServices();
  return useQuery({
    queryKey: showtimeKeys.prices(id),
    queryFn: ({ signal }) => showtimes.prices(id!, signal),
    enabled: id !== undefined,
  });
}
export function useCreateShowtime() {
  const { showtimes } = useServices();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateShowtime) => showtimes.create(input),
    onSuccess: () => client.invalidateQueries({ queryKey: showtimeKeys.lists }),
  });
}
export function useCancelShowtime() {
  const { showtimes } = useServices();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => showtimes.cancel(id),
    onSuccess: () => client.invalidateQueries({ queryKey: showtimeKeys.lists }),
  });
}
export function useReplaceSeatPrices(id: number) {
  const { showtimes } = useServices();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (prices: PriceInput[]) => showtimes.replacePrices(id, prices),
    onSuccess: async (prices) => {
      // A read started before the write must not overwrite the authoritative mutation response.
      await client.cancelQueries({ queryKey: showtimeKeys.prices(id) });
      client.setQueryData(showtimeKeys.prices(id), prices);
      await client.invalidateQueries({ queryKey: showtimeKeys.lists });
    },
  });
}
