import { useState } from "react";
import { useServices } from "../../../app/ServicesProvider";
import { VenueFields } from "../../cinema/VenueFields";
import { Button } from "../../../components/ui/Button";
import { Dialog } from "../../../components/ui/Dialog";
import { ErrorMessage } from "../../../components/ui/Feedback";
import { Field } from "../../../components/ui/Field";
import { hasStarted, localDate } from "../../../lib/date";
import {
  useCreateShowtime,
  useUpdateShowtime,
} from "../hooks/useShowtimeQueries";
import { parsePriceDraft } from "../hooks/usePriceDraft";
import type { Movie, Showtime, ShowtimeFilters } from "../model/contracts";

export function CreateShowtimeDialog({
  filters,
  movies,
  close,
  created,
  show,
}: {
  filters: ShowtimeFilters;
  movies: Movie[];
  close: () => void;
  created: (show: Showtime) => void;
  show?: Showtime;
}) {
  const create = useCreateShowtime();
  const { showtimes } = useServices();
  const [venue, setVenue] = useState<{ cinemaId?: number; screenId?: number }>(
    () => ({
      cinemaId:
        show?.cinemaId ??
        filters.cinemaId ??
        (showtimes.mode === "demo" ? 1 : undefined),
      screenId:
        show?.screenId ??
        filters.screenId ??
        (showtimes.mode === "demo" ? 1 : undefined),
    }),
  );
  const update = useUpdateShowtime(show?.id);
  const mutation = show ? update : create;
  const [error, setError] = useState<unknown>(null);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const data = new FormData(event.currentTarget);
    try {
      const startTime = `${data.get("date")}T${data.get("time")}:00`;
      if (hasStarted(startTime))
        throw new Error("Giờ bắt đầu phải nằm trong tương lai.");
      const basePrice = parsePriceDraft({ 1: String(data.get("price")) })[0]
        ?.price;
      if (basePrice === undefined) throw new Error("Vui lòng nhập giá cơ bản.");
      created(
        await mutation.mutateAsync({
          movieId: Number(data.get("movie")),
          cinemaId: venue.cinemaId!,
          screenId: venue.screenId!,
          startTime,
          basePrice,
        }),
      );
    } catch (error) {
      setError(error);
    }
  }
  return (
    <Dialog
      title={show ? "Sửa suất chiếu" : "Tạo suất chiếu"}
      onClose={close}
      busy={mutation.isPending}
    >
      <form onSubmit={submit}>
        <fieldset disabled={mutation.isPending}>
          <Field label="Phim">
            <select
              name="movie"
              defaultValue={show?.movieId ?? filters.movieId ?? ""}
              required
            >
              <option value="" disabled>
                Chọn phim
              </option>
              {movies
                .filter((m) => m.status !== "EXPIRED")
                .map((movie) => (
                  <option key={movie.id} value={movie.id}>
                    {movie.title} · {movie.durationMinutes} phút
                  </option>
                ))}
            </select>
          </Field>
          <div className="form-grid">
            <Field label="Ngày chiếu">
              <input
                name="date"
                type="date"
                min={localDate()}
                defaultValue={
                  show?.startTime.slice(0, 10) ??
                  (filters.date < localDate() ? localDate() : filters.date)
                }
                required
              />
            </Field>
            <Field label="Giờ bắt đầu">
              <input
                name="time"
                type="time"
                defaultValue={show?.startTime.slice(11, 16) ?? "18:00"}
                required
              />
            </Field>
            <VenueFields {...venue} change={setVenue} required />
          </div>
          <Field label="Giá cơ bản (VND)">
            <input
              name="price"
              inputMode="decimal"
              defaultValue={show?.basePrice ?? 90000}
              required
            />
          </Field>
        </fieldset>
        <ErrorMessage error={error} />
        <div className="dialog-actions">
          <Button disabled={mutation.isPending} onClick={close}>
            Đóng
          </Button>
          <Button variant="primary" type="submit" pending={mutation.isPending}>
            {show ? "Lưu suất chiếu" : "Tạo suất chiếu"}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
