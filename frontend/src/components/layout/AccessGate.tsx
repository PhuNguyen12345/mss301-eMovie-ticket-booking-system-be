import { useState, type ReactNode } from "react";
import { useSession } from "../../app/ServicesProvider";
import { LoginDialog } from "../../features/auth/LoginDialog";
import { Button } from "../ui/Button";
import { Placeholder } from "../ui/Placeholder";
export function AccessGate({
  children,
  admin = false,
}: {
  children: ReactNode;
  admin?: boolean;
}) {
  const session = useSession();
  const [login, setLogin] = useState(false);
  if (session.mode === "demo" && !admin)
    return (
      <div className="page">
        <Placeholder
          title="Tài khoản và đặt vé"
          description="Đặt vé và thanh toán khả dụng khi kết nối dịch vụ. Bản xem trước không tạo giao dịch."
        />
      </div>
    );
  if (admin && session.mode === "demo") return children;
  if (!session.current)
    return (
      <div className="page">
        <section className="access-card">
          <h1>Đăng nhập để tiếp tục</h1>
          <p>
            {admin
              ? "Khu vực dành cho tài khoản quản trị."
              : "Xem vé của bạn và tiếp tục thanh toán sau khi đăng nhập."}
          </p>
          <Button variant="primary" onClick={() => setLogin(true)}>
            Đăng nhập
          </Button>
        </section>
        {login && <LoginDialog close={() => setLogin(false)} />}
      </div>
    );
  if (admin && !session.isAdmin)
    return (
      <div className="page">
        <section className="access-card">
          <h1>Không có quyền truy cập</h1>
          <p>Tài khoản hiện tại không có quyền quản trị.</p>
        </section>
      </div>
    );
  return children;
}
