import { useEffect, useState } from "react";
import { Film } from "lucide-react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { LoginDialog } from "../../features/auth/LoginDialog";
import { Button } from "../ui/Button";
export function AppShell() {
  const session = useSession();
  const [login, setLogin] = useState(false);
  const location = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <>
      <a className="skip-link" href="#content">
        Đến nội dung
      </a>
      <header className="topbar app-topbar">
        <Link to="/" className="brand" aria-label="eMovie trang chủ">
          <Film size={24} />
          <strong>eMovie</strong>
        </Link>
        <nav className="main-nav" aria-label="Điều hướng chính">
          <NavLink to="/" end>
            Phim
          </NavLink>
          <NavLink to="/bookings">Vé của tôi</NavLink>
          <NavLink to="/account">Tài khoản</NavLink>
          {(session.isAdmin || session.mode === "demo") && (
            <NavLink to="/admin/showtimes">Quản trị</NavLink>
          )}
        </nav>
        <div className="topbar-actions">
          {session.mode === "demo" && (
            <span className="badge badge-demo">Dữ liệu demo</span>
          )}
          {session.current ? (
            <>
              <span className="account-name">
                {session.current.user.fullName}
              </span>
              <Button onClick={session.logout}>Đăng xuất</Button>
            </>
          ) : (
            session.mode === "api" && (
              <Button onClick={() => setLogin(true)}>Đăng nhập</Button>
            )
          )}
        </div>
      </header>
      <div id="content">
        <Outlet />
      </div>
      <footer className="site-footer">
        <strong>eMovie</strong>
        <span>Phim, lịch chiếu và vé của bạn.</span>
      </footer>
      {login && <LoginDialog close={() => setLogin(false)} />}
    </>
  );
}
