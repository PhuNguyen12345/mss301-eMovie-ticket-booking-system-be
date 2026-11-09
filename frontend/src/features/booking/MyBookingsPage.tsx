import { Link, useSearchParams } from "react-router-dom";
import { AccessGate } from "../../components/layout/AccessGate";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
import { useMyBookings } from "./hooks";
import { BookingCard } from "./BookingCard";
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
              <BookingCard key={booking.id} booking={booking} />
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
