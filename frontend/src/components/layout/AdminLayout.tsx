import { NavLink, Outlet } from "react-router-dom";
import { AccessGate } from "./AccessGate";
export function AdminLayout() {
  return (
    <AccessGate admin>
      <nav className="admin-nav" aria-label="Quản trị">
        <NavLink to="/admin/showtimes">Lịch chiếu & giá vé</NavLink>
        <NavLink to="/admin/movies">Phim</NavLink>
        <NavLink to="/admin/refunds">Hoàn tiền</NavLink>
        <NavLink to="/admin/cinema">Rạp & phòng</NavLink>
        <NavLink to="/admin/orders">Đơn vé</NavLink>
        <NavLink to="/admin/promotions">Combo & ưu đãi</NavLink>
        <NavLink to="/admin/reports">Thống kê</NavLink>
      </nav>
      <Outlet />
    </AccessGate>
  );
}
