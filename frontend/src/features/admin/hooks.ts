import { useQuery } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { BookingSearch } from "../booking/api";
import { customerIds } from "./api";
export function useBookingSearch(filters: BookingSearch) {
  const { booking } = useServices();
  const { current, isAdmin, mode } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "booking-search", filters],
    queryFn: ({ signal }) => booking.search(filters, signal),
    enabled: mode === "api" && isAdmin,
  });
}
/** Who placed the given orders; one request for the whole page. */
export function useCustomers(ids: readonly number[]) {
  const { admin } = useServices();
  const { current, isAdmin, mode } = useSession();
  const wanted = customerIds(ids);
  return useQuery({
    queryKey: ["private", current?.user.userId, "customers", wanted],
    queryFn: ({ signal }) => admin.customers(wanted, signal),
    enabled: mode === "api" && isAdmin && wanted.length > 0,
    select: (list) => new Map(list.map((c) => [c.userId, c])),
  });
}
