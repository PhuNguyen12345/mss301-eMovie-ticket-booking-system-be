import { useState } from "react";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { Dialog } from "../../components/ui/Dialog";
import { ErrorMessage } from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { formatDate, formatTime } from "../../lib/date";
import { useCheckIn } from "./hooks";

export function CheckInPage() {
  const { mode } = useSession();
  return mode === "demo" ? (
    <main className="page">
      <Placeholder
        title="Soát vé"
        description="Mở chế độ API để xác nhận khách vào rạp."
      />
    </main>
  ) : (
    <CheckIn />
  );
}
function CheckIn() {
  const mutation = useCheckIn();
  const [code, setCode] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [confirm, setConfirm] = useState<{
    bookingCode: string;
    quantity: number;
  }>();
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Soát vé tại rạp</h1>
          <p>
            Nhập mã hoặc quét bằng máy đọc QR vào ô mã đặt vé. Xác nhận đúng số
            khách trước khi cho vào.
          </p>
        </div>
      </div>
      <form
        className="surface order-filters"
        onSubmit={(e) => {
          e.preventDefault();
          mutation.reset();
          setConfirm({ bookingCode: code.trim(), quantity: Number(quantity) });
        }}
      >
        <Field label="Mã đặt vé cần soát">
          <input
            value={code}
            onChange={(e) => {
              setCode(e.target.value);
              mutation.reset();
            }}
            required
            maxLength={50}
            autoComplete="off"
          />
        </Field>
        <Field label="Số khách vào lần này">
          <input
            value={quantity}
            onChange={(e) => {
              setQuantity(e.target.value);
              mutation.reset();
            }}
            type="number"
            min={1}
            max={100}
            step={1}
            required
          />
        </Field>
        <Button type="submit" variant="primary">
          Kiểm tra và cho vào
        </Button>
      </form>
      {mutation.data && (
        <section className="surface check-in-result" role="status">
          <h2>Đã ghi nhận khách vào rạp</h2>
          <p>
            {mutation.data.bookingCode} ·{" "}
            {mutation.data.movieTitle ?? "Vé hợp lệ"}
          </p>
          <p>
            {mutation.data.cinemaName} · {mutation.data.screenName} ·{" "}
            {formatDate(mutation.data.startTime)}{" "}
            {formatTime(mutation.data.startTime)}
          </p>
          <div className="metrics-grid">
            <div className="metric">
              <span>Đã vào</span>
              <strong>
                {mutation.data.admittedCount} / {mutation.data.seatCount}
              </strong>
            </div>
            <div className="metric">
              <span>Còn lại</span>
              <strong>{mutation.data.remaining}</strong>
            </div>
          </div>
          <p>
            Ghế:{" "}
            {mutation.data.seats
              .map((s) =>
                s.rowCode && s.seatNumber
                  ? `${s.rowCode}${s.seatNumber}`
                  : `#${s.seatId}`,
              )
              .join(", ")}
          </p>
          <h3>Lịch sử soát vé</h3>
          {mutation.data.checkIns.map((c, i) => (
            <p key={i}>
              {c.quantity} khách · {formatDate(c.checkedInAt)}{" "}
              {formatTime(c.checkedInAt)} · Nhân viên #{c.checkedInBy}
            </p>
          ))}
        </section>
      )}
      {confirm && (
        <Dialog
          title="Xác nhận cho khách vào rạp?"
          onClose={() => setConfirm(undefined)}
          busy={mutation.isPending}
        >
          <p className="dialog-description">
            Ghi nhận {confirm.quantity} khách cho mã {confirm.bookingCode}. Chỉ
            cho khách vào sau khi hệ thống xác nhận thành công.
          </p>
          <ErrorMessage error={mutation.error} />
          <div className="dialog-actions">
            <Button
              disabled={mutation.isPending}
              onClick={() => setConfirm(undefined)}
            >
              Quay lại
            </Button>
            <Button
              variant="primary"
              pending={mutation.isPending}
              onClick={async () => {
                try {
                  await mutation.mutateAsync(confirm);
                  setConfirm(undefined);
                } catch {
                  /* Do not retry a check-in automatically. */
                }
              }}
            >
              Xác nhận soát vé
            </Button>
          </div>
        </Dialog>
      )}
    </main>
  );
}
