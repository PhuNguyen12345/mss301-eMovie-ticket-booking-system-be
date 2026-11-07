import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices } from "../../app/ServicesProvider";
import type {
  createCinemaApi,
  CinemaInput,
  NamedInput,
  SeatInput,
  FacilityStatus,
  Cinema,
} from "./api";

export const cinemaKeys = {
  all: ["cinema"] as const,
  list: ["cinema", "list"] as const,
  detail: (id?: number) => ["cinema", "detail", id] as const,
  screens: (id?: number) => ["cinema", "screens", id] as const,
  screen: (id?: number) => ["cinema", "screen", id] as const,
  types: ["cinema", "seat-types"] as const,
  seats: (id?: number) => ["cinema", "seats", id] as const,
};
export function useCinemas() {
  const { cinema, showtimes } = useServices();
  return useQuery({
    queryKey: cinemaKeys.list,
    queryFn: ({ signal }) => cinema.cinemas(signal),
    enabled: showtimes.mode === "api",
  });
}
export function useCinema(id?: number) {
  const { cinema, showtimes } = useServices();
  return useQuery({
    queryKey: cinemaKeys.detail(id),
    queryFn: ({ signal }) => cinema.cinema(id!, signal),
    enabled: showtimes.mode === "api" && !!id,
  });
}
export function useScreens(cinemaId?: number) {
  const { cinema, showtimes } = useServices();
  return useQuery({
    queryKey: cinemaKeys.screens(cinemaId),
    queryFn: ({ signal }) => cinema.screens(cinemaId!, signal),
    enabled: showtimes.mode === "api" && !!cinemaId,
  });
}
export function useScreen(id?: number) {
  const { cinema, showtimes } = useServices();
  return useQuery({
    queryKey: cinemaKeys.screen(id),
    queryFn: ({ signal }) => cinema.screen(id!, signal),
    enabled: showtimes.mode === "api" && !!id,
  });
}
export function useSeatTypes() {
  const { cinema, showtimes } = useServices();
  return useQuery({
    queryKey: cinemaKeys.types,
    queryFn: ({ signal }) =>
      showtimes.mode === "demo"
        ? Promise.resolve(
            showtimes.seatTypes.map((type) => ({ ...type, code: type.name })),
          )
        : cinema.seatTypes(signal),
  });
}
export function usePhysicalSeats(screenId?: number) {
  const { cinema, showtimes } = useServices();
  return useQuery({
    queryKey: cinemaKeys.seats(screenId),
    queryFn: ({ signal }) => cinema.seats(screenId!, signal),
    enabled: showtimes.mode === "api" && !!screenId,
  });
}

export type CinemaCommand =
  | { kind: "cinema"; id?: number; input: CinemaInput }
  | { kind: "screen"; id?: number; cinemaId: number; input: NamedInput }
  | { kind: "seat-type"; id?: number; input: NamedInput }
  | { kind: "seat"; screenId: number; input: SeatInput }
  | { kind: "cinema-status"; id: number; status: Cinema["status"] }
  | {
      kind: "screen-status" | "seat-status";
      id: number;
      status: FacilityStatus;
    };
function execute(
  api: ReturnType<typeof createCinemaApi>,
  command: CinemaCommand,
) {
  switch (command.kind) {
    case "cinema":
      return api.saveCinema(command.input, command.id);
    case "screen":
      return api.saveScreen(command.cinemaId, command.input, command.id);
    case "seat-type":
      return api.saveSeatType(command.input, command.id);
    case "seat":
      return api.createSeat(command.screenId, command.input);
    case "cinema-status":
      return api.cinemaStatus(command.id, command.status);
    case "screen-status":
      return api.screenStatus(command.id, command.status);
    case "seat-status":
      return api.seatStatus(command.id, command.status);
  }
}
export function useCinemaMutation() {
  const { cinema, session } = useServices();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: async (command: CinemaCommand) => {
      if (!session.getSnapshot()?.user.roles.includes("ADMIN"))
        throw new Error("Vui lòng đăng nhập tài khoản quản trị.");
      return execute(cinema, command);
    },
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: cinemaKeys.all }),
        cache.invalidateQueries({ queryKey: ["seat-map"] }),
      ]);
    },
  });
}
