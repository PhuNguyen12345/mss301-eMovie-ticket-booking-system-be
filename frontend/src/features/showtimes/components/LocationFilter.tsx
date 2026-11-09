import { useSession } from "../../../app/ServicesProvider";
import { Button } from "../../../components/ui/Button";
import { Field } from "../../../components/ui/Field";

export function LocationFilter({
  value,
  change,
}: {
  value?: string;
  change: (value?: string) => void;
}) {
  const { mode } = useSession();
  if (mode !== "api") return null;
  return (
    <form
      className="location-filter"
      onSubmit={(event) => {
        event.preventDefault();
        const location = String(
          new FormData(event.currentTarget).get("location") ?? "",
        ).trim();
        change(location || undefined);
      }}
    >
      <Field label="Địa điểm">
        <input
          key={value ?? ""}
          name="location"
          type="search"
          maxLength={255}
          defaultValue={value ?? ""}
          placeholder="Tỉnh, thành phố hoặc địa chỉ"
        />
      </Field>
      <Button type="submit">Lọc địa điểm</Button>
    </form>
  );
}
