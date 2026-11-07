import { AccountPage as Account } from "../features/account/AccountPage";
import { createBrowserRouter, Link, Navigate } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { AdminLayout } from "../components/layout/AdminLayout";
import { AccessGate } from "../components/layout/AccessGate";
import { Placeholder } from "../components/ui/Placeholder";
import { Loading } from "../components/ui/Feedback";

const pending = (title: string) => (
  <div className="page">
    <h1>{title}</h1>
    <Placeholder title={title} />
  </div>
);
export const router = createBrowserRouter([
  {
    element: <AppShell />,
    hydrateFallbackElement: <Loading label="Đang mở eMovie…" />,
    errorElement: (
      <div className="page">
        <h1>Không thể hiển thị trang</h1>
        <p>Vui lòng tải lại trang để thử lại.</p>
        <a className="button" href="/">
          Về trang chủ
        </a>
      </div>
    ),
    children: [
      {
        index: true,
        lazy: async () => ({
          Component: (await import("../features/movies/MoviesPage")).MoviesPage,
        }),
      },
      {
        path: "movies/:movieId",
        lazy: async () => ({
          Component: (await import("../features/movies/MoviePage")).MoviePage,
        }),
      },
      {
        path: "showtimes/:showtimeId/seats",
        lazy: async () => ({
          Component: (await import("../features/booking/SeatsPage")).SeatsPage,
        }),
      },
      {
        path: "bookings",
        lazy: async () => ({
          Component: (await import("../features/booking/MyBookingsPage"))
            .MyBookingsPage,
        }),
      },
      {
        path: "bookings/:bookingId",
        lazy: async () => ({
          Component: (await import("../features/booking/BookingPage"))
            .BookingPage,
        }),
      },
      {
        path: "payment/result",
        lazy: async () => ({
          Component: (await import("../features/booking/PaymentReturnPage"))
            .PaymentReturnPage,
        }),
      },
      {
        path: "account",
        element: (
          <AccessGate>
            <Account />
          </AccessGate>
        ),
      },
      { path: "register", element: pending("Đăng ký tài khoản") },
      { path: "forgot-password", element: pending("Khôi phục mật khẩu") },
      {
        path: "admin",
        element: <AdminLayout />,
        children: [
          { index: true, element: <Navigate to="showtimes" replace /> },
          {
            path: "showtimes",
            lazy: async () => ({
              Component: (
                await import("../features/showtimes/components/ShowtimesPage")
              ).ShowtimesPage,
            }),
          },
          {
            path: "movies",
            lazy: async () => ({
              Component: (await import("../features/admin/AdminMoviesPage"))
                .AdminMoviesPage,
            }),
          },
          {
            path: "refunds",
            lazy: async () => ({
              Component: (await import("../features/admin/RefundsPage"))
                .RefundsPage,
            }),
          },
          {
            path: "cinema",
            element: pending("Quản lý rạp, phòng và loại ghế"),
          },
          { path: "orders", element: pending("Tra cứu đơn vé") },
          { path: "promotions", element: pending("Combo và ưu đãi") },
          { path: "reports", element: pending("Thống kê doanh thu") },
        ],
      },
      {
        path: "*",
        element: (
          <div className="page">
            <h1>Không tìm thấy trang</h1>
            <Link className="button" to="/">
              Về trang chủ
            </Link>
          </div>
        ),
      },
    ],
  },
]);
