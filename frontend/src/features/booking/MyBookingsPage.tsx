import { Link, useSearchParams } from "react-router-dom";
import { AccessGate } from "../../components/layout/AccessGate";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
import { useMyBookings } from "./hooks";
import { BookingStatus } from "./BookingStatus";
import { formatDate, formatMoney } from "../../lib/date";
export function MyBookingsPage() {
  return (
    <AccessGate>
      <BookingList />
    </AccessGate>
  );
}
function BookingList() {
  const [params, setParams] = useSearchParams();
  const raw = Number(params.get("page") ?? 0);
  const page = Number.isSafeInteger(raw) && raw >= 0 ? raw : 0;
  const query = useMyBookings(page);
  return (
    <main className="page cinema-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">TÀI KHOẢN</p>
          <h1>Vé của tôi</h1>
          <p>Theo dõi vé đã đặt và tiếp tục thanh toán.</p>
        </div>
        <Link className="button" to="/">
          Đặt vé mới
        </Link>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorMessage
          error={query.error}
          retry={() => {
            void query.refetch();
          }}
        />
      ) : (
        <>
          <div className="booking-list">
            {query.data.data.map((booking) => (
              <Link
                key={booking.id}
                to={`/bookings/${booking.id}`}
                className="booking-card"
              >
                <div>
                  <BookingStatus status={booking.status} />
                  <h2>{booking.bookingCode}</h2>
                  <p className="muted">
                    Suất chiếu #{booking.showtimeId} · {booking.seats.length}{" "}
                    ghế · Đặt ngày {formatDate(booking.bookingTime)}
                  </p>
                </div>
                <div>
                  <strong>{formatMoney(booking.totalAmount)}</strong>
                  <span className="text-link">Xem chi tiết →</span>
                </div>
              </Link>
            ))}
          </div>
          {!query.data.data.length && (
            <EmptyState
              title="Chưa có vé"
              description="Chọn một bộ phim để bắt đầu đặt vé."
            />
          )}
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            change={(value) => setParams({ page: String(value) })}
          />
        </>
      )}
    </main>
  );
}
