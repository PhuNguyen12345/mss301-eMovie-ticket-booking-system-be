import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { Booking } from "./api";
import type { Session } from "../auth/session";

export const bookingKeys = {
  seats: (id: number) => ["seat-map", id] as const,
  show: (id: number) => ["showtimes", "detail", id] as const,
  mine: (userId?: number) => ["private", userId, "bookings"] as const,
  detail: (userId: number | undefined, id: number) =>
    ["private", userId, "booking", id] as const,
};
export function useBookingShowtime(id: number) {
  const { booking, showtimes } = useServices();
  return useQuery({
    queryKey: bookingKeys.show(id),
    queryFn: ({ signal }) => booking.showtime(id, signal),
    enabled: showtimes.mode === "api" && Number.isSafeInteger(id) && id > 0,
  });
}
export function useSeatMap(id: number, refresh = true) {
  const { booking, showtimes } = useServices();
  return useQuery({
    queryKey: bookingKeys.seats(id),
    queryFn: ({ signal }) => booking.seats(id, signal),
    enabled: showtimes.mode === "api" && Number.isSafeInteger(id) && id > 0,
    staleTime: 0,
    refetchInterval: refresh ? 10000 : false,
    refetchIntervalInBackground: false,
  });
}
export function useMyBookings(page: number) {
  const { booking } = useServices();
  const { current, mode } = useSession();
  return useQuery({
    queryKey: [...bookingKeys.mine(current?.user.userId), page],
    queryFn: ({ signal }) => booking.mine(page, signal),
    enabled: mode === "api" && !!current,
  });
}
export function useBooking(id: number) {
  const { booking } = useServices();
  const { current, mode } = useSession();
  return useQuery({
    queryKey: bookingKeys.detail(current?.user.userId, id),
    queryFn: ({ signal }) => booking.detail(id, signal),
    enabled: mode === "api" && !!current && Number.isSafeInteger(id) && id > 0,
    refetchInterval: (query) =>
      query.state.data?.status === "PENDING_PAYMENT" ? 5000 : false,
  });
}
export function useHoldSeats(showtimeId: number) {
  const { booking, session } = useServices();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (ids: number[]) => booking.hold(showtimeId, ids),
    onMutate: () => session.getSnapshot(),
    onSuccess: async (result, _ids, owner) => {
      if (!owner || session.getSnapshot()?.sessionId !== owner.sessionId)
        return;
      cache.setQueryData(
        bookingKeys.detail(owner.user.userId, result.id),
        result,
      );
      await cache.invalidateQueries({
        queryKey: bookingKeys.mine(owner.user.userId),
      });
    },
    onSettled: () => {
      void cache.invalidateQueries({ queryKey: bookingKeys.seats(showtimeId) });
    },
  });
}
export function useBookingActions(id: number) {
  const { booking, session } = useServices();
  const { current } = useSession();
  const cache = useQueryClient();
  const accept = async (
    result: Booking,
    _variables: unknown,
    owner: Session | undefined,
  ) => {
    if (!owner) return;
    const key = bookingKeys.detail(owner.user.userId, id);
    await cache.cancelQueries({ queryKey: key });
    if (session.getSnapshot()?.sessionId !== owner.sessionId) return;
    cache.setQueryData(key, result);
    await Promise.all([
      cache.invalidateQueries({
        queryKey: bookingKeys.mine(owner.user.userId),
      }),
      cache.invalidateQueries({
        queryKey: bookingKeys.seats(result.showtimeId),
      }),
    ]);
  };
  const cancel = useMutation({
    mutationFn: () => booking.cancel(id),
    onMutate: () => session.getSnapshot(),
    onSuccess: accept,
  });
  const sync = useMutation({
    mutationFn: () => booking.sync(id),
    onMutate: () => session.getSnapshot(),
    onSuccess: accept,
  });
  const pay = useMutation({
    mutationFn: () => booking.pay(id),
    onSuccess: () =>
      cache.invalidateQueries({
        queryKey: bookingKeys.detail(current?.user.userId, id),
      }),
  });
  const combos = useMutation({
    mutationFn: (lines: { comboId: number; quantity: number }[]) =>
      booking.replaceCombos(id, lines),
    onMutate: () => session.getSnapshot(),
    onSuccess: accept,
  });
  return {
    cancel,
    sync,
    pay,
    combos,
    pending:
      cancel.isPending || sync.isPending || pay.isPending || combos.isPending,
  };
}
