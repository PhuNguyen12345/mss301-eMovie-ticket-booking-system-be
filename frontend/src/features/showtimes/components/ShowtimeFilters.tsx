import { Field } from "../../../components/ui/Field";
import type { Movie, ShowtimeFilters as Filters } from "../model/contracts";
export function ShowtimeFilters({
  value,
  movies,
  update,
}: {
  value: Filters;
  movies: Movie[];
  update: (patch: Partial<Filters>) => void;
}) {
  const optionalId = (value: string) => {
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : undefined;
  };
  return (
    <div className="filters">
      <Field label="Phim">
        <select
          value={value.movieId ?? ""}
          onChange={(e) => update({ movieId: optionalId(e.target.value) })}
        >
          <option value="">Tất cả phim</option>
          {movies.map((m) => (
            <option key={m.id} value={m.id}>
              {m.title}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Mã rạp">
        <input
          type="number"
          min="1"
          step="1"
          placeholder="Tất cả"
          value={value.cinemaId ?? ""}
          onChange={(e) =>
            update({
              cinemaId: optionalId(e.target.value),
              screenId: undefined,
            })
          }
        />
      </Field>
      <Field label="Mã phòng">
        <input
          type="number"
          min="1"
          step="1"
          placeholder="Tất cả"
          value={value.screenId ?? ""}
          onChange={(e) => update({ screenId: optionalId(e.target.value) })}
        />
      </Field>
      <Field label="Trạng thái">
        <select
          value={value.status ?? ""}
          onChange={(e) =>
            update({
              status: (e.target.value || undefined) as Filters["status"],
            })
          }
        >
          <option value="SCHEDULED">Đã lên lịch</option>
          <option value="CANCELLED">Đã hủy</option>
          <option value="">Tất cả</option>
        </select>
      </Field>
    </div>
  );
}
