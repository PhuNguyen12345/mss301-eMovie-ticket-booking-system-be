import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { Pagination } from "../../components/ui/Pagination";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { formatDate, formatTime, formatMoney } from "../../lib/date";
import { usePayments } from "./hooks";
import { paymentLabels, type PaymentSearch } from "./api";

export function PaymentsPage() {
  const { mode } = useSession();
  return mode === "demo" ? (
    <main className="page">
      <Placeholder
        title="Lịch sử thanh toán"
        description="Mở chế độ API để tra cứu giao dịch."
      />
    </main>
  ) : (
    <History />
  );
}
function History() {
  const [params] = useSearchParams();
  const initialBooking = Number(params.get("bookingId")) || undefined;
  const [filters, setFilters] = useState<PaymentSearch>({
    page: 0,
    size: 20,
    bookingId: initialBooking,
  });
  const [error, setError] = useState<unknown>();
  const query = usePayments(filters);
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Lịch sử thanh toán</h1>
          <p>
            Mỗi lần thanh toán là một giao dịch riêng, gồm cả giao dịch đã hoàn.
          </p>
        </div>
      </div>
      <form
        className="surface order-filters"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          const text = (k: string) =>
            String(f.get(k) ?? "").trim() || undefined;
          const from = text("from"),
            to = text("to");
          if (from && to && from > to) {
            setError(new Error("Ngày kết thúc phải từ ngày bắt đầu trở đi."));
            return;
          }
          setError(undefined);
          setFilters({
            page: 0,
            size: 20,
            status: text("status") as PaymentSearch["status"],
            bookingId: Number(f.get("bookingId")) || undefined,
            orderCode: Number(f.get("orderCode")) || undefined,
            from,
            to,
          });
        }}
      >
        <Field label="Trạng thái giao dịch">
          <select name="status">
            <option value="">Tất cả</option>
            {Object.entries(paymentLabels).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="ID đơn vé">
          <input
            name="bookingId"
            type="number"
            min={1}
            max={2147483647}
            step={1}
            defaultValue={initialBooking}
          />
        </Field>
        <Field label="Mã giao dịch">
          <input
            name="orderCode"
            type="number"
            min={1}
            max={Number.MAX_SAFE_INTEGER}
            step={1}
          />
        </Field>
        <Field label="Tạo từ ngày">
          <input name="from" type="date" />
        </Field>
        <Field label="Tạo đến ngày">
          <input name="to" type="date" />
        </Field>
        <Button type="submit" variant="primary">
          Tìm giao dịch
        </Button>
      </form>
      <ErrorMessage
        error={error || query.error}
        retry={
          query.isError
            ? () => {
                void query.refetch();
              }
            : undefined
        }
      />
      {query.isPending ? (
        <Loading />
      ) : (
        query.data && (
          <>
            <p className="muted">{query.data.totalElements} giao dịch</p>
            <div className="catalogue-records">
              {query.data.data.map((p) => (
                <article className="surface payment-record" key={p.id}>
                  <div className="page-heading">
                    <div>
                      <h2>Giao dịch #{p.orderCode}</h2>
                      <Link to={`/admin/orders/${p.bookingId}`}>
                        {p.bookingCode} · Đơn #{p.bookingId}
                      </Link>
                      <p className="muted">
                        Khách #{p.userId} · {formatDate(p.createdAt)}{" "}
                        {formatTime(p.createdAt)}
                      </p>
                    </div>
                    <div>
                      <strong>{formatMoney(p.amount)}</strong>
                      <p className="badge">{paymentLabels[p.status]}</p>
                    </div>
                  </div>
                  <p>
                    {p.provider}
                    {p.providerTransactionId
                      ? ` · Tham chiếu ngân hàng: ${p.providerTransactionId}`
                      : ""}
                  </p>
                  {p.paidAt && (
                    <p>
                      Nhận tiền: {formatDate(p.paidAt)} {formatTime(p.paidAt)}
                    </p>
                  )}
                  {p.refunds.map((r) => (
                    <div className="notice" key={r.id}>
                      <strong>
                        {r.method === "VOUCHER"
                          ? `Hoàn bằng voucher #${r.voucherId}`
                          : "Hoàn chuyển khoản"}
                        : {formatMoney(r.amount)}
                      </strong>
                      <p>
                        {r.reason} ·{" "}
                        {
                          {
                            PENDING: "Chờ xử lý",
                            COMPLETED: "Đã hoàn",
                            REJECTED: "Đã từ chối",
                          }[r.status]
                        }
                      </p>
                    </div>
                  ))}
                </article>
              ))}
            </div>
            {!query.data.data.length && (
              <EmptyState title="Không có giao dịch phù hợp" />
            )}
            <Pagination
              page={filters.page}
              totalPages={query.data.totalPages}
              busy={query.isFetching}
              change={(page) => setFilters({ ...filters, page })}
            />
          </>
        )
      )}
    </main>
  );
}
