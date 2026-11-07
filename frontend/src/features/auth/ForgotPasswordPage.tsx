import { useState } from "react";
import { Link } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { ErrorMessage } from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { OtpField, PasswordFields, readPassword } from "./AuthFields";
import { LoginDialog } from "./LoginDialog";
import { usePasswordReset } from "./hooks";

export function ForgotPasswordPage() {
  const { mode } = useSession();
  if (mode === "demo")
    return (
      <main className="page">
        <Placeholder
          title="Khôi phục mật khẩu"
          description="Khôi phục mật khẩu khả dụng khi kết nối API."
        />
      </main>
    );
  return <ResetForm />;
}
function ResetForm() {
  const { request, confirm } = usePasswordReset();
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [done, setDone] = useState(false);
  const [login, setLogin] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const busy = request.isPending || confirm.isPending;
  async function send() {
    setError(null);
    try {
      await request.mutateAsync(email.trim());
      setSent(true);
      request.reset();
    } catch (error) {
      setError(error);
    }
  }
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!sent) {
      await send();
      return;
    }
    try {
      const form = new FormData(event.currentTarget);
      await confirm.mutateAsync({
        email: email.trim(),
        otp: String(form.get("otp")),
        newPassword: readPassword(form),
      });
      confirm.reset();
      setDone(true);
    } catch (error) {
      setError(error);
    }
  }
  return (
    <main className="page auth-page">
      <section className="surface">
        <h1>Khôi phục mật khẩu</h1>
        {done ? (
          <>
            <p role="status">
              Mật khẩu đã được cập nhật. Đăng nhập bằng mật khẩu mới.
            </p>
            <Button variant="primary" onClick={() => setLogin(true)}>
              Đăng nhập
            </Button>
          </>
        ) : (
          <form onSubmit={submit}>
            <fieldset disabled={busy}>
              <Field label="Email">
                <input
                  name="email"
                  type="email"
                  maxLength={255}
                  autoComplete="email"
                  value={email}
                  readOnly={sent}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </Field>
              {sent && (
                <>
                  <p className="notice">
                    Nếu tài khoản tồn tại, mã khôi phục sẽ được gửi đến email
                    này. Kiểm tra cả thư mục spam.
                  </p>
                  <OtpField />
                  <PasswordFields />
                  <div className="auth-links">
                    <Button
                      onClick={() => {
                        void send();
                      }}
                    >
                      Gửi lại mã OTP
                    </Button>
                    <Button
                      onClick={() => {
                        setSent(false);
                        setError(null);
                      }}
                    >
                      Đổi email
                    </Button>
                  </div>
                </>
              )}
            </fieldset>
            <ErrorMessage error={error} />
            <Button type="submit" variant="primary" pending={busy}>
              {sent ? "Đặt lại mật khẩu" : "Gửi mã khôi phục"}
            </Button>
          </form>
        )}
        <Link to="/" className="text-link">
          Về danh sách phim
        </Link>
      </section>
      {login && <LoginDialog close={() => setLogin(false)} />}
    </main>
  );
}
