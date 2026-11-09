import { z } from "zod";
import type { HttpClient } from "../../lib/http";

const id = z.number().int().positive().safe();
export const facilityStatus = z.enum(["ACTIVE", "INACTIVE", "MAINTENANCE"]);
export const cinemaSchema = z.object({
  id,
  code: z.string(),
  name: z.string(),
  location: z.string(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
});
export const screenSchema = z.object({
  id,
  cinemaId: id,
  code: z.string(),
  name: z.string(),
  status: facilityStatus,
});
export const seatTypeSchema = z.object({
  id,
  code: z.string(),
  name: z.string(),
});
export const seatSchema = z.object({
  id,
  screenId: id,
  rowCode: z.string(),
  seatNumber: id,
  seatTypeId: id,
  status: facilityStatus,
});
export type Cinema = z.infer<typeof cinemaSchema>;
export type Screen = z.infer<typeof screenSchema>;
export type SeatType = z.infer<typeof seatTypeSchema>;
export type Seat = z.infer<typeof seatSchema>;
export type FacilityStatus = z.infer<typeof facilityStatus>;
export type CinemaInput = Pick<Cinema, "code" | "name" | "location">;
export type NamedInput = Pick<Screen, "code" | "name">;
export type SeatInput = Pick<Seat, "rowCode" | "seatNumber" | "seatTypeId">;

export function createCinemaApi(client: HttpClient) {
  return {
    cinemas: (signal?: AbortSignal) =>
      client.request("/cinemas", z.array(cinemaSchema), { signal }),
    cinema: (id: number, signal?: AbortSignal) =>
      client.request(`/cinemas/${id}`, cinemaSchema, { signal }),
    screens: (cinemaId: number, signal?: AbortSignal) =>
      client.request(`/cinemas/${cinemaId}/screens`, z.array(screenSchema), {
        signal,
      }),
    screen: (id: number, signal?: AbortSignal) =>
      client.request(`/screens/${id}`, screenSchema, { signal }),
    seatTypes: (signal?: AbortSignal) =>
      client.request("/seat-types", z.array(seatTypeSchema), { signal }),
    seats: (screenId: number, signal?: AbortSignal) =>
      client.request(`/screens/${screenId}/seats`, z.array(seatSchema), {
        signal,
      }),
    saveCinema: (body: CinemaInput, id?: number) =>
      client.request(id ? `/cinemas/${id}` : "/cinemas", cinemaSchema, {
        method: id ? "PUT" : "POST",
        body,
      }),
    saveScreen: (cinemaId: number, body: NamedInput, id?: number) =>
      client.request(
        id ? `/screens/${id}` : `/cinemas/${cinemaId}/screens`,
        screenSchema,
        { method: id ? "PUT" : "POST", body },
      ),
    saveSeatType: (body: NamedInput, id?: number) =>
      client.request(id ? `/seat-types/${id}` : "/seat-types", seatTypeSchema, {
        method: id ? "PUT" : "POST",
        body,
      }),
    createSeat: (screenId: number, body: SeatInput) =>
      client.request(`/screens/${screenId}/seats`, seatSchema, {
        method: "POST",
        body,
      }),
    cinemaStatus: (id: number, status: Cinema["status"]) =>
      client.request(`/cinemas/${id}/status`, cinemaSchema, {
        method: "PATCH",
        body: { status },
      }),
    screenStatus: (id: number, status: FacilityStatus) =>
      client.request(`/screens/${id}/status`, screenSchema, {
        method: "PATCH",
        body: { status },
      }),
    seatStatus: (id: number, status: FacilityStatus) =>
      client.request(`/seats/${id}/status`, seatSchema, {
        method: "PATCH",
        body: { status },
      }),
  };
}
