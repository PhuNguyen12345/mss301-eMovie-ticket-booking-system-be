import { Link, useSearchParams } from "react-router-dom";
import { AccessGate } from "../../components/layout/AccessGate";
import { EmptyState } from "../../components/ui/Feedback";
import { BookingDetails } from "./BookingPage";
import { bookingIdFromOrderCode } from "./api";
export function PaymentReturnPage() {
  const [params] = useSearchParams();
  const id = bookingIdFromOrderCode(params.get("orderCode"));
  return (
    <AccessGate>
      {id ? (
        <BookingDetails key={id} id={id} fromPayment />
      ) : (
        <main className="page">
          <EmptyState
            title="Kiểm tra kết quả thanh toán"
            description="Mở đơn vé của bạn để xác nhận trạng thái giao dịch."
          />
          <Link className="button button-primary" to="/bookings">
            Xem vé của tôi
          </Link>
        </main>
      )}
    </AccessGate>
  );
}
