import { Field } from "../../components/ui/Field";
import { ErrorMessage } from "../../components/ui/Feedback";
import { useGenreOptions } from "./hooks";

export function GenreFilter({
  value,
  change,
}: {
  value?: number;
  change: (id?: number) => void;
}) {
  const query = useGenreOptions();
  if (!query.isEnabled) return null;
  return (
    <div className="genre-filter">
      <Field label="Thể loại">
        <select
          value={value ?? ""}
          onChange={(e) => change(Number(e.target.value) || undefined)}
        >
          <option value="">
            {query.isPending ? "Đang tải thể loại…" : "Tất cả thể loại"}
          </option>
          {value && !query.data?.some((row) => row.id === value) && (
            <option value={value}>Thể loại #{value}</option>
          )}
          {query.data?.map((row) => (
            <option key={row.id} value={row.id}>
              {row.name}
            </option>
          ))}
        </select>
      </Field>
      <ErrorMessage
        error={query.error}
        retry={() => {
          void query.refetch();
        }}
      />
    </div>
  );
}
