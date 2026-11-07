import { useState } from "react";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
import { Placeholder } from "../../components/ui/Placeholder";
import { BookingCard } from "../booking/BookingCard";
import type { BookingSearch } from "../booking/api";
import { useBookingSearch } from "./hooks";
export function OrdersPage() {
  const { mode } = useSession();
  if (mode === "demo")
    return (
      <main className="page">
        <Placeholder
          title="Tra cứu đơn vé"
          description="Mở chế độ API để tra cứu đơn vé thật."
        />
      </main>
    );
  return <OrderSearch />;
}
function OrderSearch() {
  const [filters, setFilters] = useState<BookingSearch>({ page: 0, size: 10 });
  const [error, setError] = useState<unknown>(null);
  const query = useBookingSearch(filters);
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Tra cứu đơn vé</h1>
          <p>Tìm theo mã đặt vé, khách hàng, suất chiếu hoặc thời gian đặt.</p>
        </div>
      </div>
      <form
        className="order-filters surface"
        onSubmit={(e) => {
          e.preventDefault();
          setError(null);
          const form = new FormData(e.currentTarget);
          const text = (key: string) =>
            String(form.get(key) ?? "").trim() || undefined;
          const from = text("from"),
            to = text("to");
          if (from && to && from >= to) {
            setError(
              new Error("Thời điểm kết thúc phải sau thời điểm bắt đầu."),
            );
            return;
          }
          setFilters({
            page: 0,
            size: 10,
            code: text("code"),
            status: text("status"),
            userId: Number(form.get("userId")) || undefined,
            showtimeId: Number(form.get("showtimeId")) || undefined,
            from,
            to,
          });
        }}
      >
        <Field label="Mã đặt vé">
          <input name="code" placeholder="Nhập đầy đủ mã đặt vé" />
        </Field>
        <Field label="Trạng thái đơn">
          <select name="status">
            <option value="">Tất cả</option>
            <option value="PENDING_PAYMENT">Chờ thanh toán</option>
            <option value="CONFIRMED">Đã xác nhận</option>
            <option value="CANCELLED">Đã hủy</option>
            <option value="EXPIRED">Hết hạn</option>
          </select>
        </Field>
        <Field label="ID khách hàng">
          <input name="userId" type="number" min={1} step={1} />
        </Field>
        <Field label="ID suất chiếu">
          <input name="showtimeId" type="number" min={1} step={1} />
        </Field>
        <Field label="Đặt từ (giờ Việt Nam)">
          <input name="from" type="datetime-local" />
        </Field>
        <Field label="Đặt trước (giờ Việt Nam)">
          <input name="to" type="datetime-local" />
        </Field>
        <Button type="submit" variant="primary">
          Tìm đơn vé
        </Button>
      </form>
      <ErrorMessage error={error} />
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
          <p className="muted">{query.data.totalElements} đơn vé</p>
          <div className="booking-list">
            {query.data.data.map((booking) => (
              <BookingCard key={booking.id} booking={booking} admin />
            ))}
          </div>
          {!query.data.data.length && (
            <EmptyState title="Không có đơn vé phù hợp" />
          )}
          <Pagination
            page={filters.page}
            totalPages={query.data.totalPages}
            change={(page) => setFilters((previous) => ({ ...previous, page }))}
          />
        </>
      )}
    </main>
  );
}
