import { formatDate, formatMoney, formatTime, serverTimestamp } from "./date";

type Details = Record<string, unknown>;

const text = (value: unknown) =>
  typeof value === "string" ? value : undefined;
const number = (value: unknown) =>
  typeof value === "number" && Number.isFinite(value) ? value : undefined;
const at = (value: string) => `${formatTime(value)} ngày ${formatDate(value)}`;

const VOUCHER_REASONS: Record<string, string> = {
  NO_MATCHING_COMBO: "Đơn chưa có combo mà voucher này áp dụng.",
  COMBO_ALREADY_COVERED: "Các phần combo này đều đã được áp voucher.",
  SEAT_NOT_IN_BOOKING: "Ghế đã chọn không thuộc đơn này.",
  SEAT_ALREADY_COVERED: "Ghế này đã được áp một voucher đổi vé khác.",
  SEAT_TYPE_MISMATCH: "Voucher không áp dụng cho loại ghế này.",
  NOTHING_TO_DISCOUNT: "Đơn không còn khoản nào để voucher này giảm.",
  ONE_PER_KIND: "Mỗi đơn chỉ dùng được một voucher loại này.",
};

const CLOSED_BOOKING: Record<string, string> = {
  PENDING_PAYMENT: "Đơn này chưa thanh toán nên chưa thể soát vé.",
  CANCELLED: "Đơn này đã bị hủy nên không thể soát vé.",
  EXPIRED: "Đơn này đã hết hạn giữ ghế, chưa được thanh toán.",
  SHOWTIME_CANCELLED:
    "Suất chiếu của đơn này đã bị hủy, khách đã được bù bằng voucher.",
};

const FIXED: Record<string, string> = {
  INVALID_REQUEST: "Thông tin chưa hợp lệ, vui lòng kiểm tra lại.",
  NOT_FOUND: "Không tìm thấy dữ liệu yêu cầu.",
  CONFLICT:
    "Thao tác không thực hiện được vì trạng thái đơn đã thay đổi. Vui lòng tải lại trang.",
  CONCURRENT_UPDATE:
    "Đơn vừa được cập nhật ở nơi khác. Vui lòng tải lại trang rồi thử lại.",
  BOOKING_NOT_FOUND: "Không tìm thấy đơn vé. Vui lòng kiểm tra lại mã đặt vé.",
  SHOWTIME_NOT_OPEN: "Suất chiếu này đã bắt đầu hoặc không còn mở bán.",
  SEAT_UNAVAILABLE: "Ghế bạn chọn vừa có người đặt. Vui lòng chọn ghế khác.",
  SEAT_OUT_OF_SERVICE: "Có ghế đang bảo trì. Vui lòng chọn ghế khác.",
  BOOKING_NOT_PENDING: "Đơn không còn ở trạng thái chờ thanh toán.",
  HOLD_EXPIRED: "Đã hết thời gian giữ ghế. Vui lòng đặt lại.",
  PAYMENT_LINK_OPEN:
    "Đơn đang có link thanh toán nên không đổi được combo hoặc voucher. Hãy hoàn tất thanh toán hoặc đợi link hết hạn.",
  PAYMENT_IN_PROGRESS:
    "Hệ thống đang tạo link thanh toán. Vui lòng đợi vài giây rồi thử lại.",
  PAYMENT_ATTEMPTS_EXCEEDED:
    "Đơn đã tạo link thanh toán quá số lần cho phép. Vui lòng đặt vé mới.",
  COMBO_NOT_FOUND: "Combo không tồn tại.",
  COMBO_UNAVAILABLE: "Combo này đã ngừng bán.",
  VOUCHER_NOT_FOUND: "Mã voucher không tồn tại, đã có chủ hoặc đã hết hạn.",
  VOUCHER_ALREADY_APPLIED: "Voucher này đã được áp dụng cho đơn.",
  VOUCHER_EXPIRED: "Voucher đã hết hạn.",
  VOUCHER_IN_USE: "Voucher đang được dùng cho một đơn khác.",
  VOUCHER_INACTIVE: "Voucher đã ngừng áp dụng.",
  VOUCHER_NOT_OWNED: "Voucher này thuộc về tài khoản khác.",
  VOUCHER_SEAT_REQUIRED: "Hãy chọn ghế muốn dùng voucher đổi vé.",
};

function checkInWindow(data: Details) {
  const opens = text(data.opensAt),
    closes = text(data.closesAt),
    start = text(data.startTime);
  if (!opens || !closes) return undefined;
  const show = start ? `Suất ${at(start)}` : "Suất chiếu này";
  return Date.now() < serverTimestamp(opens)
    ? `Chưa đến giờ soát vé. ${show} soát vé từ ${formatTime(opens)} đến ${formatTime(closes)}.`
    : `Đã hết giờ soát vé. ${show} chỉ soát vé đến ${formatTime(closes)}.`;
}

function alreadyUsed(data: Details) {
  const seats = number(data.seatCount);
  const scans = Array.isArray(data.checkIns) ? data.checkIns : [];
  const last = scans.length
    ? text((scans[scans.length - 1] as Details | null)?.checkedInAt)
    : undefined;
  return [
    seats
      ? `Vé này đã vào rạp đủ ${seats} khách.`
      : "Vé này đã vào rạp đủ số khách.",
    last ? `Lần soát gần nhất lúc ${at(last)}.` : "",
  ]
    .filter(Boolean)
    .join(" ");
}

/**
 * Vietnamese text for a booking-service error code, using the details it sends; undefined for codes this app
 * does not know, so the caller can fall back to the server message.
 */
export function translateError(
  code: string,
  data: unknown,
): string | undefined {
  const details: Details =
    data && typeof data === "object" ? (data as Details) : {};
  switch (code) {
    case "CHECK_IN_NOT_OPEN":
      return (
        checkInWindow(details) ??
        "Hiện không trong giờ soát vé của suất chiếu này."
      );
    case "CHECK_IN_NOT_PAID":
      return (
        CLOSED_BOOKING[text(details.status) ?? ""] ??
        "Đơn này chưa thể soát vé."
      );
    case "CHECK_IN_TOO_MANY": {
      const remaining = number(details.remaining);
      return remaining === undefined
        ? "Số khách vượt quá số vé còn lại của đơn."
        : `Đơn này chỉ còn ${remaining} khách chưa vào rạp.`;
    }
    case "TICKET_ALREADY_USED":
      return alreadyUsed(details);
    case "SEAT_LIMIT": {
      const max = number(details.max);
      return max
        ? `Mỗi đơn chỉ được giữ tối đa ${max} ghế.`
        : "Bạn đã chọn quá số ghế cho phép.";
    }
    case "COMBO_QUANTITY": {
      const max = number(details.max);
      return max
        ? `Mỗi combo chỉ được chọn từ 1 đến ${max} phần.`
        : "Số lượng combo không hợp lệ.";
    }
    case "VOUCHER_NOT_STARTED": {
      const start = text(details.startDate);
      return start
        ? `Voucher dùng được từ ngày ${formatDate(start)}.`
        : "Voucher chưa đến ngày sử dụng.";
    }
    case "VOUCHER_MIN_ORDER": {
      const min = number(details.minOrderAmount);
      return min
        ? `Voucher chỉ áp dụng cho đơn từ ${formatMoney(min)}.`
        : "Đơn chưa đạt giá trị tối thiểu của voucher.";
    }
    case "VOUCHER_MIN_PAYMENT": {
      const min = number(details.minPayment);
      return `Sau khi giảm, số tiền cần trả phải bằng 0 hoặc từ ${formatMoney(min ?? 2000)} trở lên, vì ngân hàng không nhận chuyển khoản nhỏ hơn.`;
    }
    case "VOUCHER_NOT_APPLICABLE":
      return (
        VOUCHER_REASONS[text(details.reason) ?? ""] ??
        "Voucher không áp dụng được cho đơn này."
      );
    default:
      return FIXED[code];
  }
}
