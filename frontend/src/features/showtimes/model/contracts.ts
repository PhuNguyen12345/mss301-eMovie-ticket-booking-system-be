import { z } from "zod";

const id = z.number().int().positive().safe();
const price = z.number().finite().min(0).max(9999999999.99);
const dateTime = z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/);
export const showtimeSchema = z.object({
  id,
  movieId: id,
  cinemaId: id,
  screenId: id,
  cinemaLocation: z.string().optional(),
  startTime: dateTime,
  endTime: dateTime,
  basePrice: price,
  status: z.enum(["SCHEDULED", "CANCELLED"]),
});
export const seatPriceSchema = z.object({
  id: id.optional(),
  seatTypeId: id,
  price,
});
export const movieSchema = z.object({
  id,
  title: z.string().min(1),
  durationMinutes: z.number().int().positive(),
  ageRating: z.string().nullish(),
  status: z.string().optional(),
});
export const pageSchema = <T extends z.ZodType>(item: T) =>
  z.object({
    data: z.array(item),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
    first: z.boolean(),
    last: z.boolean(),
  });
export type Showtime = z.infer<typeof showtimeSchema>;
export type SeatPrice = z.infer<typeof seatPriceSchema>;
export type Movie = z.infer<typeof movieSchema>;
export type Page<T> = {
  data: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
export type ShowtimeFilters = {
  date: string;
  location?: string;
  cinemaId?: number;
  screenId?: number;
  movieId?: number;
  status?: Showtime["status"];
  page: number;
  size: number;
};
export type CreateShowtime = Pick<
  Showtime,
  "movieId" | "cinemaId" | "screenId" | "startTime" | "basePrice"
>;
export type PriceInput = Pick<SeatPrice, "seatTypeId" | "price">;
export type SeatType = { id: number; name: string };

export interface ShowtimeRepository {
  readonly mode: "demo" | "api";
  readonly seatTypes: SeatType[];
  list(filters: ShowtimeFilters, signal?: AbortSignal): Promise<Page<Showtime>>;
  movies(signal?: AbortSignal): Promise<Movie[]>;
  prices(id: number, signal?: AbortSignal): Promise<SeatPrice[]>;
  create(input: CreateShowtime): Promise<Showtime>;
  update(id: number, input: CreateShowtime): Promise<Showtime>;
  replacePrices(id: number, prices: PriceInput[]): Promise<SeatPrice[]>;
  cancel(id: number): Promise<Showtime>;
}
