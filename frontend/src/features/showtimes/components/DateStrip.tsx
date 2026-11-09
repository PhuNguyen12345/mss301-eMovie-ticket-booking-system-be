import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "../../../components/ui/Button";
import { addDays, localDate } from "../../../lib/date";
export function DateStrip({
  value,
  start,
  select,
  move,
}: {
  value: string;
  start: string;
  select: (date: string) => void;
  move: (offset: number) => void;
}) {
  return (
    <div className="week-picker">
      <Button variant="ghost" aria-label="Tuần trước" onClick={() => move(-1)}>
        <ChevronLeft size={18} />
      </Button>
      {Array.from({ length: 7 }, (_, index) => {
        const date = addDays(start, index);
        const parsed = new Date(`${date}T12:00:00`);
        return (
          <button
            className={`day ${value === date ? "selected" : ""}`}
            key={date}
            aria-pressed={value === date}
            onClick={() => select(date)}
          >
            <span>
              {date === localDate()
                ? "Hôm nay"
                : parsed.toLocaleDateString("vi-VN", { weekday: "short" })}
            </span>
            <strong>{String(parsed.getDate()).padStart(2, "0")}</strong>
          </button>
        );
      })}
      <Button variant="ghost" aria-label="Tuần sau" onClick={() => move(1)}>
        <ChevronRight size={18} />
      </Button>
    </div>
  );
}
