import { useQuery } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { BookingSearch } from "../booking/api";
export function useBookingSearch(filters: BookingSearch) {
  const { booking } = useServices();
  const { current, isAdmin, mode } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "booking-search", filters],
    queryFn: ({ signal }) => booking.search(filters, signal),
    enabled: mode === "api" && isAdmin,
  });
}
