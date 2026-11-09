import { useState } from "react";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { localDate, addDays, formatMoney } from "../../lib/date";
import { useStatistics } from "./hooks";
import type { StatisticsSearch } from "./api";

export function ReportsPage() {
  const { mode } = useSession();
  return mode === "demo" ? (
    <main className="page">
      <Placeholder
        title="Thống kê doanh thu"
        description="Mở chế độ API để xem số liệu thực tế."
      />
    </main>
  ) : (
    <Report />
  );
}
function Report() {
  const [filters, setFilters] = useState<StatisticsSearch>({
    from: addDays(localDate(), -29),
    to: localDate(),
    groupBy: "day",
  });
  const [error, setError] = useState<unknown>();
  const query = useStatistics(filters);
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Thống kê doanh thu</h1>
          <p>
            Theo ngày chiếu (giờ Việt Nam), bao gồm cả ngày đầu và ngày cuối.
          </p>
        </div>
        <Button
          disabled={query.isFetching}
          onClick={() => {
            void query.refetch();
          }}
        >
          Làm mới
        </Button>
      </div>
      <form
        className="surface order-filters"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          const from = String(f.get("from")),
            to = String(f.get("to"));
          if (from > to) {
            setError(new Error("Ngày kết thúc phải từ ngày bắt đầu trở đi."));
            return;
          }
          setError(undefined);
          setFilters({
            from,
            to,
            groupBy: String(f.get("group")) as StatisticsSearch["groupBy"],
          });
        }}
      >
        <Field label="Từ ngày chiếu">
          <input name="from" type="date" required defaultValue={filters.from} />
        </Field>
        <Field label="Đến ngày chiếu">
          <input name="to" type="date" required defaultValue={filters.to} />
        </Field>
        <Field label="Nhóm theo">
          <select name="group" defaultValue={filters.groupBy}>
            <option value="day">Ngày</option>
            <option value="month">Tháng</option>
            <option value="movie">Phim</option>
            <option value="cinema">Rạp</option>
          </select>
        </Field>
        <Button type="submit" variant="primary">
          Xem thống kê
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
            <p className="muted">
              Kết quả {query.data.from} – {query.data.to}. Doanh thu tính từ đơn
              đã xác nhận; khoản hoàn voucher hiển thị riêng.
            </p>
            <div className="metrics-grid">
              {[
                ["Doanh thu", formatMoney(query.data.total.revenue)],
                ["Vé đã bán", query.data.total.tickets],
                ["Đơn xác nhận", query.data.total.bookings],
                ["Giảm giá", formatMoney(query.data.total.discountAmount)],
                ["Hoàn voucher", formatMoney(query.data.total.refundedAmount)],
              ].map(([label, value]) => (
                <section className="surface metric" key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </section>
              ))}
            </div>
            <div
              className="report-table-wrap"
              tabIndex={0}
              aria-label="Bảng doanh thu"
            >
              <table className="report-table">
                <caption>
                  Chi tiết theo{" "}
                  {
                    {
                      day: "ngày",
                      month: "tháng",
                      movie: "phim",
                      cinema: "rạp",
                    }[filters.groupBy]
                  }
                </caption>
                <thead>
                  <tr>
                    <th>Nhóm</th>
                    <th>Đơn</th>
                    <th>Vé</th>
                    <th>Tiền vé</th>
                    <th>Combo</th>
                    <th>Giảm giá</th>
                    <th>Doanh thu</th>
                    <th>Hoàn voucher</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.rows.map((r, i) => (
                    <tr key={r.key ?? `unknown-${i}`}>
                      <th>{r.label || r.key || "Chưa có thông tin"}</th>
                      <td>{r.figures.bookings}</td>
                      <td>{r.figures.tickets}</td>
                      <td>{formatMoney(r.figures.ticketAmount)}</td>
                      <td>{formatMoney(r.figures.comboAmount)}</td>
                      <td>{formatMoney(r.figures.discountAmount)}</td>
                      <td>{formatMoney(r.figures.revenue)}</td>
                      <td>{formatMoney(r.figures.refundedAmount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!query.data.rows.length && (
              <EmptyState title="Chưa có số liệu trong khoảng ngày này" />
            )}
          </>
        )
      )}
    </main>
  );
}
