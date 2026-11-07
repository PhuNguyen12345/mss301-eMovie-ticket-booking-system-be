import { useServices } from "../../app/ServicesProvider";
import { Field } from "../../components/ui/Field";
import { ErrorMessage } from "../../components/ui/Feedback";
import { useCinemas, useScreens } from "./hooks";

export function VenueFields({
  cinemaId,
  screenId,
  change,
  required = false,
}: {
  cinemaId?: number;
  screenId?: number;
  change: (value: { cinemaId?: number; screenId?: number }) => void;
  required?: boolean;
}) {
  const { showtimes } = useServices();
  const cinemas = useCinemas();
  const screens = useScreens(cinemaId);
  if (showtimes.mode === "demo")
    return (
      <>
        <Field label="Mã rạp">
          <input
            name="cinema"
            type="number"
            min="1"
            step="1"
            value={cinemaId ?? ""}
            required={required}
            onChange={(e) =>
              change({
                cinemaId: Number(e.target.value) || undefined,
                screenId: required ? 1 : undefined,
              })
            }
          />
        </Field>
        <Field label="Mã phòng">
          <input
            name="screen"
            type="number"
            min="1"
            step="1"
            value={screenId ?? ""}
            required={required}
            onChange={(e) =>
              change({
                cinemaId,
                screenId: Number(e.target.value) || undefined,
              })
            }
          />
        </Field>
      </>
    );
  return (
    <>
      <Field label="Rạp">
        <select
          name="cinema"
          required={required}
          value={cinemaId ?? ""}
          onChange={(e) =>
            change({
              cinemaId: Number(e.target.value) || undefined,
              screenId: undefined,
            })
          }
        >
          <option value="">
            {cinemas.isPending
              ? "Đang tải rạp…"
              : required
                ? "Chọn rạp"
                : "Tất cả rạp"}
          </option>
          {cinemas.data?.map((cinema) => (
            <option
              key={cinema.id}
              value={cinema.id}
              disabled={required && cinema.status !== "ACTIVE"}
            >
              {cinema.name} · {cinema.location}
              {cinema.status !== "ACTIVE" ? " (Ngừng hoạt động)" : ""}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Phòng chiếu">
        <select
          name="screen"
          required={required}
          value={screenId ?? ""}
          onChange={(e) =>
            change({ cinemaId, screenId: Number(e.target.value) || undefined })
          }
        >
          <option value="">
            {!cinemaId
              ? "Chọn rạp trước"
              : screens.isPending
                ? "Đang tải phòng…"
                : required
                  ? "Chọn phòng"
                  : "Tất cả phòng"}
          </option>
          {screens.data?.map((screen) => (
            <option
              key={screen.id}
              value={screen.id}
              disabled={required && screen.status !== "ACTIVE"}
            >
              {screen.name}
              {screen.status !== "ACTIVE" ? " (Không hoạt động)" : ""}
            </option>
          ))}
        </select>
      </Field>
      <ErrorMessage
        error={cinemas.error}
        retry={() => {
          void cinemas.refetch();
        }}
      />
      <ErrorMessage
        error={screens.error}
        retry={() => {
          void screens.refetch();
        }}
      />
    </>
  );
}
