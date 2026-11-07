import type { SeatMap } from "./api";
import { formatMoney } from "../../lib/date";
const names = {
  AVAILABLE: "Còn trống",
  HELD: "Đang giữ",
  BOOKED: "Đã đặt",
  UNAVAILABLE: "Không khả dụng",
};
export function SeatGrid({
  seats,
  selected,
  toggle,
  disabled,
}: {
  seats: SeatMap["seats"];
  selected: number[];
  toggle: (id: number) => void;
  disabled: boolean;
}) {
  const rows = [...new Set(seats.map((s) => s.rowCode))].sort((a, b) =>
    a.localeCompare(b, "vi", { numeric: true }),
  );
  const columns = Math.max(1, ...seats.map((seat) => seat.seatNumber));
  return (
    <>
      <div className="screen-line">MÀN HÌNH</div>
      <div
        className="seat-map-scroll"
        tabIndex={0}
        role="region"
        aria-label="Sơ đồ ghế, cuộn ngang nếu cần"
      >
        <div className="seat-map">
          {rows.map((row) => (
            <div
              className="seat-row"
              key={row}
              style={{
                display: "grid",
                gridTemplateColumns: `24px repeat(${columns}, 40px)`,
              }}
            >
              <span className="row-label" aria-hidden="true">
                {row}
              </span>
              {seats
                .filter((s) => s.rowCode === row)
                .sort((a, b) => a.seatNumber - b.seatNumber)
                .map((seat) => (
                  <button
                    type="button"
                    key={seat.seatId}
                    style={{ gridColumn: seat.seatNumber + 1 }}
                    className={`seat seat-${seat.status.toLowerCase()} ${selected.includes(seat.seatId) ? "selected" : ""}`}
                    disabled={disabled || seat.status !== "AVAILABLE"}
                    aria-pressed={selected.includes(seat.seatId)}
                    aria-label={`Ghế ${seat.rowCode}${seat.seatNumber}, ${formatMoney(seat.price)}, ${names[seat.status]}`}
                    onClick={() => toggle(seat.seatId)}
                  >
                    {seat.rowCode}
                    {seat.seatNumber}
                  </button>
                ))}
            </div>
          ))}
        </div>
      </div>
      <div className="seat-legend">
        <span>
          <i className="seat-available" />
          Còn trống
        </span>
        <span>
          <i className="selected" />
          Đang chọn
        </span>
        <span>
          <i className="seat-held" />
          Đang giữ
        </span>
        <span>
          <i className="seat-booked" />
          Đã đặt / không khả dụng
        </span>
      </div>
    </>
  );
}
