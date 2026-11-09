import type { Booking } from "./api";
const labels: Record<Booking["status"], string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  CONFIRMED: "Đã thanh toán",
  CANCELLED: "Đã hủy",
  SHOWTIME_CANCELLED: "Suất chiếu đã hủy",
  EXPIRED: "Hết hạn giữ ghế",
};
export function BookingStatus({ status }: { status: Booking["status"] }) {
  return (
    <span
      className={`badge ${status === "CONFIRMED" ? "" : status === "PENDING_PAYMENT" ? "badge-demo" : "badge-cancelled"}`}
    >
      {labels[status]}
    </span>
  );
}
