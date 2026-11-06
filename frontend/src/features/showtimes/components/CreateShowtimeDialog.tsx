import { useState } from "react";
import { Button } from "../../../components/ui/Button";
import { Dialog } from "../../../components/ui/Dialog";
import { ErrorMessage } from "../../../components/ui/Feedback";
import { Field } from "../../../components/ui/Field";
import { hasStarted, localDate } from "../../../lib/date";
import { useCreateShowtime } from "../hooks/useShowtimeQueries";
import { parsePriceDraft } from "../hooks/usePriceDraft";
import type { Movie, Showtime, ShowtimeFilters } from "../model/contracts";

export function CreateShowtimeDialog({
  filters,
  movies,
  close,
  created,
}: {
  filters: ShowtimeFilters;
  movies: Movie[];
  close: () => void;
  created: (show: Showtime) => void;
}) {
  const mutation = useCreateShowtime();
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
          cinemaId: Number(data.get("cinema")),
          screenId: Number(data.get("screen")),
          startTime,
          basePrice,
        }),
      );
    } catch (error) {
      setError(error);
    }
  }
  return (
    <Dialog title="Tạo suất chiếu" onClose={close} busy={mutation.isPending}>
      <form onSubmit={submit}>
        <fieldset disabled={mutation.isPending}>
          <Field label="Phim">
            <select name="movie" defaultValue={filters.movieId ?? ""} required>
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
                  filters.date < localDate() ? localDate() : filters.date
                }
                required
              />
            </Field>
            <Field label="Giờ bắt đầu">
              <input name="time" type="time" defaultValue="18:00" required />
            </Field>
            <Field label="Mã rạp">
              <input
                name="cinema"
                type="number"
                min="1"
                step="1"
                defaultValue={filters.cinemaId ?? 1}
                required
              />
            </Field>
            <Field label="Mã phòng">
              <input
                name="screen"
                type="number"
                min="1"
                step="1"
                defaultValue={filters.screenId ?? 1}
                required
              />
            </Field>
          </div>
          <Field label="Giá cơ bản (VND)">
            <input
              name="price"
              inputMode="decimal"
              defaultValue="90000"
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
            Tạo suất chiếu
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
