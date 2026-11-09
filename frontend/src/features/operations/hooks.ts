import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { PaymentSearch, StatisticsSearch } from "./api";

export function usePayments(filters: PaymentSearch) {
  const { operations } = useServices();
  const { current, isAdmin, mode } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "payments", filters],
    queryFn: ({ signal }) => operations.payments(filters, signal),
    enabled: mode === "api" && isAdmin,
  });
}
export function useStatistics(filters: StatisticsSearch) {
  const { operations } = useServices();
  const { current, isAdmin, mode } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "statistics", filters],
    queryFn: ({ signal }) => operations.statistics(filters, signal),
    enabled: mode === "api" && isAdmin,
  });
}
export function useCheckIn() {
  const { operations, session } = useServices();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (input: { bookingCode: string; quantity?: number }) =>
      operations.checkIn(input.bookingCode, input.quantity),
    onMutate: () => session.getSnapshot(),
    onSuccess: async (_data, _input, owner) => {
      if (owner && session.getSnapshot()?.sessionId === owner.sessionId)
        await cache.invalidateQueries({
          queryKey: ["private", owner.user.userId],
        });
    },
  });
}
export function useCancellation() {
  const { operations, session } = useServices();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => operations.cancellation(id),
    onMutate: () => session.getSnapshot(),
    onSuccess: async (_data, id, owner) => {
      if (owner && session.getSnapshot()?.sessionId === owner.sessionId)
        await Promise.all([
          cache.invalidateQueries({ queryKey: ["private", owner.user.userId] }),
          cache.invalidateQueries({ queryKey: ["seat-map", id] }),
          cache.invalidateQueries({ queryKey: ["showtimes"] }),
        ]);
    },
  });
}
